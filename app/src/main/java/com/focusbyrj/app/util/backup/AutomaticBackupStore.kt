package com.focusbyrj.app.util.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** No test/software fallback in production: missing or unusable device keys require recovery-key import. */
internal interface BackupKeyWrapper { fun prepareForImport() = Unit; fun wrap(bytes: ByteArray, purpose: String): ByteArray; fun unwrap(bytes: ByteArray, purpose: String): ByteArray }
internal class AndroidBackupKeyWrapper : BackupKeyWrapper {
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(ALIAS)) return (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw SecurityException("The device backup key is unavailable. Import your exported recovery key.")
        check(create) { "The device backup key is missing. Import your exported recovery key." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }
    override fun wrap(bytes: ByteArray, purpose: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key(false))
        require(c.iv.size == 12); c.updateAAD(("RUN automatic backups v1:" + purpose).toByteArray())
        return byteArrayOf(1) + c.iv + c.doFinal(bytes)
    }
    override fun prepareForImport() {
        try {
            val probe = Cipher.getInstance("AES/GCM/NoPadding")
            probe.init(Cipher.ENCRYPT_MODE, key(true))
            probe.doFinal(byteArrayOf()) // Finish the hardware operation rather than leaving it open until GC.
        }
        catch (_: KeyPermanentlyInvalidatedException) {
            // Explicit creation/import already supplies the portable key. Replace only an
            // irreversibly invalid wrapper, never a transient failure and never on a read.
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
            key(true)
        }
    }
    override fun unwrap(bytes: ByteArray, purpose: String): ByteArray {
        require(bytes.size in 30..4096 && bytes[0] == 1.toByte())
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        c.updateAAD(("RUN automatic backups v1:" + purpose).toByteArray())
        return c.doFinal(bytes, 13, bytes.size - 13)
    }
    companion object { private const val ALIAS = "run_automatic_backup_wrapper_v1" }
}

internal class BackupVaultChangedException : SecurityException(
    "Vault protection changed. Reconnect your original recovery file to continue."
)

/** Device-local configuration/ledger and wrapped secrets are deliberately excluded from portable backup preferences. */
internal class AutomaticBackupStore(context: Context, private val wrapper: BackupKeyWrapper = AndroidBackupKeyWrapper()) {
    private val prefs = context.applicationContext.getSharedPreferences("automatic_full_backups_v1", Context.MODE_PRIVATE)
    private val stagingFolder = java.io.File(context.applicationContext.noBackupFilesDir, "automatic_backup_staging")
    fun config(): AutomaticBackupConfig = prefs.getString("config", null)?.let(AutomaticBackupConfig::read) ?: AutomaticBackupConfig()
    fun save(config: AutomaticBackupConfig) { config.validate(); check(prefs.edit().putString("config", config.json().toString()).commit()); changed() }
    fun hasKey() = prefs.contains("wrapped_key")
    fun install(key: BackupRecoveryKey, resetVaultBinding: Boolean = false) {
        val old = runCatching { config() }.getOrElse { AutomaticBackupConfig() }
        val priorId = old.keyId.ifBlank { key.id }
        val priorOrigin = if (hasKey()) vaultOrigin(priorId) else null
        val plain = key.credential().toByteArray()
        try {
            wrapper.prepareForImport() // Only explicit generation/import can create a missing wrapping key.
            val wrapped = wrapper.wrap(plain, "recovery")
            verifyWrapped(wrapped, plain, "recovery")
            val same = old.keyId == key.id
            val next = if (same) old.copy(enabled = false, keyExported = false, keyAcknowledged = false, nextDue = 0, problem = "", detail = "") else old.copy(enabled = false, keyId = key.id,
                stream = UUID.randomUUID().toString(), keyExported = false, keyAcknowledged = false, nextDue = 0,
                lastSuccess = 0, lastAttempt = 0, problem = "", detail = "", lastFile = "")
            next.validate()
            val editor = prefs.edit().remove("wrapped_vault").remove("stage").putString("vault_auth_generation", UUID.randomUUID().toString())
                .putString("request_epoch", UUID.randomUUID().toString())
            if (priorOrigin != null) editor.putString(originName(priorId), priorOrigin)
            // A stored-key repair proves no knowledge of the recovery credential. Only
            // explicit external-key import or a newly generated key can transfer this binding.
            if (resetVaultBinding) editor.remove(originName(key.id))
            editor.putBoolean("vault_origin_tracking", true)
            // Explicit recovery/new-key setup can quarantine a damaged ownership ledger without deleting files.
            if (runCatching { records() }.isFailure) editor.putString("quarantined_records", prefs.all["records"]?.toString()?.take(8_000_000))
                .putString("records", "[]").putBoolean("ownership_limit_reached", true)
            check(editor.putString("wrapped_key", Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putString("config", next.json().toString()).commit())
            discardUnownedStages()
            changed()
        } finally { plain.fill(0) }
    }
    fun key(): BackupRecoveryKey {
        val id = config().keyId
        val key = storedKey()
        if (key.id != id) { key.close(); error("Stored recovery key does not match this setup. Repair the automatic backup configuration.") }
        return key
    }
    /** Independent of nonsecret configuration, so a damaged schedule cannot make the wrapped key inaccessible. */
    fun storedKey(): BackupRecoveryKey {
        val blob = prefs.getString("wrapped_key", null) ?: error("Create or import a backup recovery key first.")
        require(blob.length <= 8192) { "The protected recovery key is damaged. Import your exported key." }
        val bytes = wrapper.unwrap(Base64.decode(blob, Base64.NO_WRAP), "recovery")
        return try { BackupRecoveryKey.parse(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
    }
    fun authorizeVault(key: ByteArray) {
        require(key.size == 32)
        this.key().use { } // Never generate/replace a missing device key while authorizing a vault.
        requireVaultIdentity(key)
        val generation = UUID.randomUUID().toString()
        val purpose = "private-vault:${config().keyId}:$generation"
        val wrapped = wrapper.wrap(key, purpose)
        verifyWrapped(wrapped, key, purpose)
        check(prefs.edit().putString("wrapped_vault", Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(originName(config().keyId), fingerprint(key))
            .putString("vault_auth_generation", generation).commit())
        changed()
    }
    fun hasVaultAuthorization() = prefs.contains("wrapped_vault")
    fun vaultKey(): ByteArray? = prefs.getString("wrapped_vault", null)?.let {
        require(it.length <= 8192) { "The private-vault backup authorization is damaged. Authorize it again." }
        wrapper.unwrap(Base64.decode(it, Base64.NO_WRAP), "private-vault:${config().keyId}:${vaultAuthorizationToken()}").also { bytes ->
            if (bytes.size != 32) { bytes.fill(0); error("The private-vault backup authorization is invalid. Authorize it again.") }
        }
    }
    private fun verifyWrapped(wrapped: ByteArray, expected: ByteArray, purpose: String) {
        val actual = wrapper.unwrap(wrapped, purpose)
        try { check(MessageDigest.isEqual(actual, expected)) { "The protected key could not be verified. Existing credentials were kept." } }
        finally { actual.fill(0) }
    }
    private fun originName(id: String) = "private_vault_origin_$id"
    private fun fingerprint(key: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest("RUN private backup vault identity v1\u0000".toByteArray() + key).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun vaultOrigin(id: String): String? {
        val saved = prefs.all[originName(id)]
        if (saved != null) return (saved as? String)?.takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: "unverified"
        if (hasVaultAuthorization()) {
            return try { vaultKey()?.let { key -> try { fingerprint(key) } finally { key.fill(0) } } ?: "unverified" }
            catch (_: Exception) { "unverified" }
        }
        if (prefs.all["vault_origin_tracking"] != true) {
            // Older local metadata cannot distinguish ordinary and private archives after
            // authorization was revoked. Preserve confidentiality until the key is imported.
            if (runCatching { records().any { it.keyId == id } }.getOrDefault(true)) return "unverified"
        }
        return null
    }
    fun requireVaultIdentity(activeKey: ByteArray?) {
        val origin = vaultOrigin(config().keyId) ?: return
        if (activeKey == null || origin != fingerprint(activeKey)) throw BackupVaultChangedException()
    }
    fun vaultAuthorizationToken() = prefs.getString("vault_auth_generation", "none") ?: "none"
    fun requestEpoch() = (prefs.all["request_epoch"] as? String)?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) } ?: "invalid"
    fun invalidateRequests() { check(prefs.edit().putString("request_epoch", UUID.randomUUID().toString()).commit()); changed() }
    fun revokeVault(): Int {
        val id = config().keyId; val origin = vaultOrigin(id)
        check(prefs.edit().apply { if (origin != null) putString(originName(id), origin) }
            .remove("wrapped_vault").remove("stage")
            .putString("request_epoch", UUID.randomUUID().toString())
            .putString("vault_auth_generation", UUID.randomUUID().toString()).commit()); changed()
        return discardUnownedStages()
    }
    /** Only app-owned, uncommitted encrypted stages are eligible. External backups are never touched. */
    fun discardUnownedStages(): Int {
        // Do not guess ownership if the saved stage metadata is damaged.
        val owned = runCatching { staged()?.uri?.let { android.net.Uri.parse(it).path } }.getOrElse { return 0 }
        return stagingFolder.listFiles()?.count { file ->
            file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.enc")) && file.absolutePath != owned &&
                !file.delete() && file.exists()
        } ?: 0
    }
    fun records(): List<ManagedBackup> {
        val raw = prefs.getString("records", "[]")!!; require(raw.length <= 8_000_000)
        BackupArchiveLimits.checkJsonNesting(raw)
        val values = JSONArray(raw); require(values.length() <= 5000)
        return (0 until values.length()).map { ManagedBackup.read(values.getJSONObject(it)) }
    }
    fun records(records: List<ManagedBackup>) {
        // Broken provider deletion must not eventually prevent creating fresh protection.
        // Overflow relinquishes ownership of the oldest entries; their external files are kept.
        val retained = ArrayList<String>()
        var size = 2 // JSON array brackets; the read bound is in UTF-16 characters.
        for (record in records.asReversed().take(5000)) {
            val json = record.json().toString()
            if (size + json.length + 1 > 8_000_000) break
            retained.add(json); size += json.length + 1
        }
        val edit = prefs.edit().putString("records", retained.asReversed().joinToString(prefix = "[", postfix = "]", separator = ","))
        if (retained.size < records.size) edit.putBoolean("ownership_limit_reached", true)
        check(edit.commit()); changed()
    }
    fun ownershipLimitReached() = prefs.getBoolean("ownership_limit_reached", false)
    fun staged(): ManagedBackup? = prefs.getString("stage", null)?.let {
        require(it.length <= 16000); BackupArchiveLimits.checkJsonNesting(it); ManagedBackup.read(org.json.JSONObject(it))
    }
    /** Discard trust in damaged metadata, never in the user's files or recovery credential. */
    fun repairBookkeeping() {
        val editor = prefs.edit()
        var repaired = false
        if (runCatching { records() }.isFailure) {
            editor.putString("quarantined_records", prefs.all["records"]?.toString()?.take(8_000_000))
                .putString("records", "[]").putBoolean("ownership_limit_reached", true)
            repaired = true
        }
        if (runCatching { staged() }.isFailure) {
            editor.putString("quarantined_stage", prefs.all["stage"]?.toString()?.take(16000)).remove("stage")
            repaired = true
        }
        if (repaired) { check(editor.commit()); changed() }
    }
    fun staged(value: ManagedBackup?) {
        check(prefs.edit().apply { if (value == null) remove("stage") else putString("stage", value.json().toString()) }.commit()); changed()
    }
    companion object {
        private val changes = MutableStateFlow(0L)
        val revision = changes.asStateFlow()
        private fun changed() { changes.value = changes.value + 1 }
    }
}
