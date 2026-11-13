package com.focusbyrj.app.util.crypto

import android.content.Context
import android.util.Base64
import androidx.core.util.AtomicFile
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteEntity
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.hpke.HPKE
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.json.JSONObject

/**
 * A write-only public key permits adding private notes without retaining the VDK or PIN.
 * The matching private key is GCM-wrapped by the VDK. Each note carries that authenticated
 * wrapper, so disabling/reconfiguring this option never strands previously archived notes.
 * RFC 9180 HPKE base mode: X25519 / HKDF-SHA256 / AES-256-GCM.
 */
internal object VaultArchiveDeposit {
    const val ENVELOPE_MARKER = "sealed-v1:"
    private const val MAX_CONFIGURATION_BYTES = 8192L
    private const val MAX_ENVELOPE_CHARS = 64 * 1024 * 1024
    private val fileLock = Any()
    private val random = SecureRandom()
    private val payloadInfo = "RUN/archive-deposit/v1/payload".toByteArray()
    private val privateInfo = "RUN/archive-deposit/v1/private".toByteArray()

    internal class WriteCapability internal constructor(
        internal val publicKey: ByteArray,
        internal val privateIv: ByteArray,
        internal val wrappedPrivate: ByteArray,
        internal val binding: String
    )

    internal class Authorization internal constructor(internal val key: ByteArray, internal val binding: String) : AutoCloseable {
        override fun close() { key.fill(0) }
    }

    fun captureAuthorization(context: Context): Authorization = synchronized(ArchiveVaultSecurity) {
        check(ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED)
        val key = ArchiveVaultSecurity.getActiveVaultSubKey() ?: throw IllegalStateException("Confirm your vault PIN first.")
        try { Authorization(key, binding(context)) } catch (error: Throwable) { key.fill(0); throw error }
    }

    private fun hpke() = HPKE(HPKE.mode_base, HPKE.kem_X25519_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256)
    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "vault_archive_deposit.enc")
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String, size: Int): ByteArray {
        require(value.length <= ((size + 2) / 3) * 4) { "Invalid archive key size" }
        return Base64.decode(value, Base64.NO_WRAP).also { require(it.size == size) { "Invalid archive key size" } }
    }
    private fun binding(context: Context): String = ArchiveVaultSecurity.archiveWriteBinding(context)

    private fun aad(capability: WriteCapability) = payloadInfo + capability.publicKey + capability.privateIv + capability.wrappedPrivate
    private fun crypt(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray, input: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            require(key.size == 32 && iv.size == 12)
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)); updateAAD(aad); doFinal(input)
        }

    /** No unwrapped private key is persisted; the caller owns and wipes its authorized VDK. */
    fun enable(context: Context, authorization: Authorization) {
        require(ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED)
        val vaultKey = authorization.key
        require(vaultKey.size == 32)
        val expected = authorization.binding
        check(expected == binding(context)) { "Vault settings changed. Confirm your PIN again." }
        val suite = hpke()
        val pair = suite.generatePrivateKey()
        val public = suite.serializePublicKey(pair.public)
        val private = suite.serializePrivateKey(pair.private)
        val iv = ByteArray(12).also(random::nextBytes)
        val wrapped = try { crypt(Cipher.ENCRYPT_MODE, vaultKey, iv, privateInfo + public, private) }
            finally { private.fill(0) }
        val capability = WriteCapability(public, iv, wrapped, expected)
        // Verify a complete round trip before publishing a new configuration.
        val probe = "RUN archive deposit self-check".toByteArray()
        val sender = suite.setupBaseS(suite.deserializePublicKey(public), payloadInfo)
        val sealed = sender.seal(aad(capability), probe)
        val opened = openParts(capability, sender.encapsulation, sealed, vaultKey)
        try { check(probe.contentEquals(opened)) } finally { opened.fill(0); probe.fill(0) }
        val bytes = JSONObject().put("version", 1).put("binding", expected).put("public", b64(public))
            .put("iv", b64(iv)).put("wrapped", b64(wrapped)).toString().toByteArray()
        try {
            synchronized(fileLock) {
                check(expected == binding(context)) { "Vault settings changed. Confirm your PIN and try again." }
                EncryptedMediaStorage.writeEncryptedBytes(file(context), bytes)
                check(readCapability(context) != null) { "The archive option could not be verified." }
            }
        } finally { bytes.fill(0) }
    }

    fun disable(context: Context) = synchronized(fileLock) {
        val target = file(context)
        AtomicFile(target).delete()
        check(listOf(target, File(target.path + ".bak"), File(target.path + ".new")).none { it.exists() }) {
            "The archive option could not be disabled. Check phone storage and retry."
        }
    }

    /** Missing/tampered/stale metadata always falls back to asking for the PIN. */
    fun readCapability(context: Context): WriteCapability? = synchronized(fileLock) {
        try {
            if (ArchiveVaultSecurity.getVaultStatus(context) != ArchiveVaultSecurity.VaultStatus.ENABLED) return@synchronized null
            val target = file(context)
            if (target.length() !in 1..MAX_CONFIGURATION_BYTES) return@synchronized null
            val bytes = EncryptedMediaStorage.readDecryptedBytes(target) ?: return@synchronized null
            try {
                val json = JSONObject(bytes.decodeToString())
                require(json.getInt("version") == 1 && json.getString("binding") == binding(context))
                WriteCapability(decode(json.getString("public"), 32), decode(json.getString("iv"), 12),
                    decode(json.getString("wrapped"), 48), json.getString("binding"))
            } finally { bytes.fill(0) }
        } catch (_: Exception) { null }
    }

    fun encrypt(context: Context, note: NoteEntity, capability: WriteCapability): NoteEntity {
        require(note.isArchived && !VaultPayloadEncryptor.isVaultEncrypted(note))
        check(capability.binding == binding(context)) { "Vault settings changed. Start the move again." }
        val suite = hpke()
        val sender = suite.setupBaseS(suite.deserializePublicKey(capability.publicKey), payloadInfo)
        val plaintext = VaultPayloadEncryptor.encodePayload(note)
        val ciphertext = try { sender.seal(aad(capability), plaintext) } finally { plaintext.fill(0) }
        val envelope = "ENC_VAULT_V1:kv=${VaultPayloadEncryptor.CURRENT_KEY_VERSION}:" + ENVELOPE_MARKER +
            listOf(capability.publicKey, capability.privateIv, capability.wrappedPrivate, sender.encapsulation, ciphertext).joinToString(":") { b64(it) }
        require(envelope.length <= MAX_ENVELOPE_CHARS) { "This note is too large to archive without PIN. Your draft was kept." }
        return VaultPayloadEncryptor.protectedNote(note, envelope)
    }

    fun open(envelope: String, vaultKey: ByteArray): ByteArray {
        require(envelope.length <= MAX_ENVELOPE_CHARS)
        val parts = envelope.split(":", limit = 6)
        require(parts.size == 6 && parts[0] == "sealed-v1") { "Invalid sealed archive envelope" }
        val capability = WriteCapability(decode(parts[1], 32), decode(parts[2], 12), decode(parts[3], 48), "")
        val encapsulation = decode(parts[4], 32)
        val ciphertext = Base64.decode(parts[5], Base64.NO_WRAP)
        require(ciphertext.size >= 16)
        return openParts(capability, encapsulation, ciphertext, vaultKey)
    }

    private fun openParts(capability: WriteCapability, encapsulation: ByteArray, ciphertext: ByteArray, vaultKey: ByteArray): ByteArray {
        val private = crypt(Cipher.DECRYPT_MODE, vaultKey, capability.privateIv, privateInfo + capability.publicKey, capability.wrappedPrivate)
        return try {
            require(private.size == 32)
            require(X25519PrivateKeyParameters(private).generatePublicKey().encoded.contentEquals(capability.publicKey))
            val suite = hpke()
            val pair = suite.deserializePrivateKey(private, capability.publicKey)
            suite.setupBaseR(encapsulation, pair, payloadInfo).open(aad(capability), ciphertext)
        } finally { private.fill(0) }
    }
}
