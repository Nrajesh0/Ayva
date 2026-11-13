/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

import android.content.Context
import androidx.room.withTransaction
import android.util.Base64
import android.util.Log
import com.focusbyrj.app.util.backup.SnapshotCryptoEngine
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max

/** PIN-derived KEK wraps a stable VDK. Only a domain-separated one-way verifier is retained.
 * Recovery and separately authorized unattended backups can also recover the VDK.
 * A locked UI cannot defend against a fully compromised OS or running app process.
 */
object ArchiveVaultSecurity {

    private const val TAG = "ArchiveVaultSecurity"
    const val PREFS_NAME = "focus_notes_archive_vault_security"
    const val KEY_STATUS = "vault_status" // "not_configured", "enabled", "disabled"
    private const val KEY_SALT = "enc_salt"
    private const val KEY_HASH = "pin_verifier_v2"
    private const val KEY_IV = "enc_iv"
    private const val KEY_KDF_TYPE = "kdf_type"
    private const val CURRENT_KDF = "argon2id-verifier-v2"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until_epoch_ms"
    private const val KEY_RECOVERY_CIPHERTEXT = "rec_ciphertext"
    private const val KEY_RECOVERY_IV = "rec_iv"
    private const val KEY_RECOVERY_SALT = "rec_salt"
    private const val KEY_RECOVERY_ENABLED = "rec_enabled"
    private const val KEY_RECOVERY_PHRASE_CIPHERTEXT = "rec_phrase_ciphertext"
    private const val KEY_RECOVERY_PHRASE_IV = "rec_phrase_iv"
    const val KEY_RECOVERY_BACKED_UP = "rec_phrase_backed_up"

    // Stable Vault Data Key (VDK) envelope constants (Work Package 4)
    const val KEY_WRAPPED_VAULT_DATA_KEY = "vdk_wrapped"
    const val KEY_WRAPPED_VAULT_DATA_KEY_IV = "vdk_iv"
    const val KEY_VAULT_DATA_KEY_VERSION = "vdk_version"

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTE_LENGTH = 16

    /**
     * Wraps a 256-bit Vault Data Key (VDK) using a Key Encryption Key (KEK) via AES-256-GCM.
     */
    fun wrapVaultDataKey(vdk: ByteArray, kek: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val wrapped = cipher.doFinal(vdk)
        return Pair(wrapped, iv)
    }

    /**
     * Unwraps a 256-bit Vault Data Key (VDK) using a Key Encryption Key (KEK) via AES-256-GCM.
     */
    fun unwrapVaultDataKey(wrappedVdk: ByteArray, iv: ByteArray, kek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(wrappedVdk)
    }

    /** Initial setup can be interrupted after credentials commit. Retry sealing with
     * the authenticated VDK; a failure never exposes a partially opened vault. */
    private fun sealArchivedNotes(context: Context, vdk: ByteArray) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO + com.focusbyrj.app.util.backup.MaintenanceGate.inheritedContext()) {
            val db = NoteDatabase.getInstance(context)
            db.withTransaction {
                db.noteDao().getArchivedNotesSync().forEach { note ->
                    if (!VaultPayloadEncryptor.isVaultEncrypted(note)) db.noteDao().updateNote(VaultPayloadEncryptor.encryptNotePayload(note, vdk))
                }
            }
        }
    }

    /** A verifier can authenticate a derived KEK but cannot unwrap the VDK. */
    internal fun pinVerifier(kek: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update("Ayva PIN verifier v2\u0000".toByteArray(Charsets.UTF_8)); digest(kek)
    }

    // Ephemeral in-memory Vault Sub-Key cache while vault is unlocked
    @Volatile
    private var ephemeralVaultSubKey: ByteArray? = null

    // Monotonic clock lockout timestamp (immune to device clock manipulation)
    @Volatile
    private var inMemoryLockoutElapsed: Long = 0L

    /** Public write capabilities must be invalidated by any change to authentication metadata.
     * SharedPreferences.all is one atomic snapshot; no vault/file lock inversion is introduced. */
    internal fun archiveWriteBinding(context: Context): String {
        val values = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
        check(values[KEY_STATUS] == "enabled" && values[KEY_KDF_TYPE] == CURRENT_KDF && values[KEY_VAULT_DATA_KEY_VERSION] == 1) {
            "Vault credentials need attention. Unlock the vault before enabling PIN-free archiving."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("RUN/archive-write-binding/v1\u0000".toByteArray(Charsets.UTF_8))
        for (name in listOf(KEY_SALT, KEY_HASH, KEY_KDF_TYPE, KEY_WRAPPED_VAULT_DATA_KEY, KEY_WRAPPED_VAULT_DATA_KEY_IV)) {
            val value = values[name] as? String ?: error("Vault credentials are incomplete; existing data was kept.")
            require(value.isNotEmpty() && value.length <= 8192)
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    /**
     * Retrieves the active in-memory Vault Sub-Key if vault is unlocked, or null if locked.
     * Returns a defensive copy to prevent external callers zeroing the master subkey in-place.
     */
    @Synchronized
    fun getActiveVaultSubKey(): ByteArray? = ephemeralVaultSubKey?.copyOf()

    /**
     * Locks the vault and completely wipes the in-memory sub-key bytes.
     */
    @Synchronized
    fun lockVault() {
        ephemeralVaultSubKey?.let { Arrays.fill(it, 0.toByte()) }
        ephemeralVaultSubKey = null
    }

    enum class VaultStatus {
        NOT_CONFIGURED,
        ENABLED,
        DISABLED
    }

    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Incorrect(val remainingAttempts: Int) : VerifyResult()
        data class LockedOut(val remainingSeconds: Long) : VerifyResult()
        data class Error(val message: String) : VerifyResult()
    }

    /**
     * Checks current vault configuration status.
     */
    fun getVaultStatus(context: Context): VaultStatus {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_STATUS, "not_configured")) {
            "enabled" -> VaultStatus.ENABLED
            "disabled" -> VaultStatus.DISABLED
            else -> VaultStatus.NOT_CONFIGURED
        }
    }

    fun isVaultLocked(context: Context): Boolean {
        return getVaultStatus(context) == VaultStatus.ENABLED && ephemeralVaultSubKey == null
    }

    /**
     * Returns the current version of the Vault Data Key (VDK), or 0 if legacy/unconfigured.
     */
    fun getVaultDataKeyVersion(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_VAULT_DATA_KEY_VERSION, 0)
    }

    /**
     * Checks whether BIP-39 mnemonic phrase emergency recovery is configured for this vault.
     */
    fun isRecoveryConfigured(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_RECOVERY_ENABLED, false) &&
                prefs.getString(KEY_RECOVERY_CIPHERTEXT, null) != null &&
                prefs.getString(KEY_RECOVERY_SALT, null) != null &&
                prefs.getString(KEY_RECOVERY_IV, null) != null
    }

    /**
     * Configures a new 6-digit passcode for the Archive Secret Vault using Argon2id.
     * Optionally creates an encrypted recovery envelope derived from a 12-word BIP-39 emergency phrase.
     */
    /**
     * Configures a new 6-digit passcode for the Archive Secret Vault using Argon2id.
     * Optionally creates an encrypted recovery envelope derived from a 12-word BIP-39 emergency phrase.
     * Persists the recovery phrase encrypted with AES-256-GCM under the vault subkey for Zero-Knowledge recovery & export.
     */
    @Synchronized
    fun setPasscode(
        context: Context,
        pin: String,
        mnemonicWords: List<String>? = null,
        isPhraseBackedUp: Boolean = true
    ): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            Log.e(TAG, "Invalid PIN format: must be exactly 6 digits")
            return false
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_BYTE_LENGTH)
        secureRandom.nextBytes(salt)

        val status = getVaultStatus(context)
        val oldSubKey = ephemeralVaultSubKey?.copyOf()
        if (status == VaultStatus.ENABLED && oldSubKey == null) {
            Log.e(TAG, "Cannot change passcode: vault is currently locked. Old subkey required to re-encrypt data.")
            return false
        }
        val isChangingPasscode = (status == VaultStatus.ENABLED && oldSubKey != null)

        val pinChars = pin.toCharArray()
        var derivedHash: ByteArray? = null
        var vaultDataKey: ByteArray? = null
        try {
            // Use real Argon2id as primary KDF for the Key Encryption Key (KEK)
            derivedHash = deriveArgon2idHash(pinChars, salt)

            if (isChangingPasscode && (prefs.getString(KEY_KDF_TYPE, null) != CURRENT_KDF || !prefs.contains(KEY_WRAPPED_VAULT_DATA_KEY))) {
                throw SecurityException("Unsupported development vault format; credentials and data retained")
            }
            val hasExistingVdk = isChangingPasscode
            vaultDataKey = if (hasExistingVdk) {
                // WP4: Stable VDK already in place! ZERO note rewrites required!
                // oldSubKey IS the stable VDK.
                oldSubKey!!.copyOf()
            } else {
                // Brand-new vault: generate a fresh 256-bit VDK
                ByteArray(32).also { SecureRandom().nextBytes(it) }
            }

            val finalVdk = vaultDataKey ?: return false

            // Wrap stable VDK with new PIN KEK (derivedHash)
            val (wrappedVdk, vdkIv) = wrapVaultDataKey(finalVdk, derivedHash)

            val verifier = pinVerifier(derivedHash)

            // Check if there is an existing recovery phrase when changing passcode
            val existingWords = if (isChangingPasscode) {
                getStoredRecoveryPhraseInternal(prefs, oldSubKey)
            } else null

            val effectiveWords = mnemonicWords ?: existingWords

            check(!isChangingPasscode || !prefs.getBoolean(KEY_RECOVERY_ENABLED, false) || effectiveWords != null) {
                "Recovery credentials could not be authenticated; PIN was not changed"
            }
            require(effectiveWords == null || effectiveWords.size == 12) { "Invalid recovery phrase" }
            val recoveryEnvelope = effectiveWords?.let { VaultCryptoEngine.createRecoveryEnvelope(finalVdk, it) }
            val encryptedPhrase = effectiveWords?.let { encryptRecoveryPhrase(it, finalVdk) }

            val editor = prefs.edit()
                .putString(KEY_STATUS, "enabled")
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(SnapshotCryptoEngine.encryptSnapshot(verifier), Base64.NO_WRAP))
                .remove(KEY_IV)
                .remove("enc_hash")
                .putString(KEY_KDF_TYPE, CURRENT_KDF)
                .putString(KEY_WRAPPED_VAULT_DATA_KEY, Base64.encodeToString(SnapshotCryptoEngine.encryptSnapshot(wrappedVdk), Base64.NO_WRAP))
                .putString(KEY_WRAPPED_VAULT_DATA_KEY_IV, Base64.encodeToString(vdkIv, Base64.NO_WRAP))
                .putInt(KEY_VAULT_DATA_KEY_VERSION, 1)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)

            if (recoveryEnvelope != null && encryptedPhrase != null) {
                val backedUpStatus = if (mnemonicWords != null) isPhraseBackedUp else prefs.getBoolean(KEY_RECOVERY_BACKED_UP, false)
                editor.putBoolean(KEY_RECOVERY_ENABLED, true)
                    .putString(KEY_RECOVERY_CIPHERTEXT, Base64.encodeToString(recoveryEnvelope.first, Base64.NO_WRAP))
                    .putString(KEY_RECOVERY_IV, Base64.encodeToString(recoveryEnvelope.second, Base64.NO_WRAP))
                    .putString(KEY_RECOVERY_SALT, Base64.encodeToString(recoveryEnvelope.third, Base64.NO_WRAP))
                    .putString(KEY_RECOVERY_PHRASE_CIPHERTEXT, Base64.encodeToString(encryptedPhrase.first, Base64.NO_WRAP))
                    .putString(KEY_RECOVERY_PHRASE_IV, Base64.encodeToString(encryptedPhrase.second, Base64.NO_WRAP))
                    .putBoolean(KEY_RECOVERY_BACKED_UP, backedUpStatus)
            } else if (isChangingPasscode) {
                // Changing passcode without any mnemonic invalidates previous recovery envelope to prevent desync
                editor.putBoolean(KEY_RECOVERY_ENABLED, false)
                    .remove(KEY_RECOVERY_CIPHERTEXT)
                    .remove(KEY_RECOVERY_IV)
                    .remove(KEY_RECOVERY_SALT)
                    .remove(KEY_RECOVERY_PHRASE_CIPHERTEXT)
                    .remove(KEY_RECOVERY_PHRASE_IV)
                    .remove(KEY_RECOVERY_BACKED_UP)
            }

            val committed = editor.commit()
            // Set ephemeralVaultSubKey and perform note encryption only after prefs are safely on disk.
            if (committed) {
                ephemeralVaultSubKey?.fill(0)
                ephemeralVaultSubKey = finalVdk.copyOf()

                if (!hasExistingVdk) {
                    sealArchivedNotes(appContext, finalVdk)
                }
            }
            return committed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set vault passcode", e)
            if (status != VaultStatus.ENABLED) lockVault()
            return false
        } finally {
            Arrays.fill(pinChars, '0')
            derivedHash?.let { Arrays.fill(it, 0.toByte()) }
            oldSubKey?.let { Arrays.fill(it, 0.toByte()) }
            vaultDataKey?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Verifies the 6-digit PIN using the current Argon2id verifier and authenticated VDK envelope.
     * Protected by progressive brute-force rate-limiting and timing-attack-resistant comparison.
     */
    @Synchronized
    fun verifyPasscode(context: Context, inputPin: String): VerifyResult {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val elapsed = android.os.SystemClock.elapsedRealtime()

        // 1. Check brute-force lockout status (immune to device clock manipulation)
        if (elapsed < inMemoryLockoutElapsed) {
            val remainingSec = minOf(300L, max(1L, (inMemoryLockoutElapsed - elapsed + 999L) / 1000L))
            return VerifyResult.LockedOut(remainingSec)
        }

        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            val remainingSec = minOf(300L, max(1L, (lockoutUntil - now + 999L) / 1000L))
            inMemoryLockoutElapsed = elapsed + (remainingSec * 1000L)
            return VerifyResult.LockedOut(remainingSec)
        }

        // 2. Validate input format
        if (inputPin.length != 6 || !inputPin.all { it.isDigit() }) {
            // Accurately report remaining attempts based on actual failed attempts, not hardcoded 5
            val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
            return VerifyResult.Incorrect(remainingAttempts = maxOf(0, 5 - currentAttempts))
        }

        if (prefs.getString(KEY_KDF_TYPE, null) != CURRENT_KDF) {
            return VerifyResult.Error("Unsupported development vault format. Existing data has been retained.")
        }
        val pinChars = inputPin.toCharArray()
        var computedHash: ByteArray? = null
        var expectedHash: ByteArray? = null
        var activeVdk: ByteArray? = null
        try {
            val salt = Base64.decode(requireNotNull(prefs.getString(KEY_SALT, null)), Base64.NO_WRAP)
            expectedHash = SnapshotCryptoEngine.decryptSnapshot(Base64.decode(requireNotNull(prefs.getString(KEY_HASH, null)), Base64.NO_WRAP))
            require(salt.size == SALT_BYTE_LENGTH && expectedHash.size == 32)
            computedHash = deriveArgon2idHash(pinChars, salt)
            val actualVerifier = pinVerifier(computedHash)
            val matches = try { MessageDigest.isEqual(actualVerifier, expectedHash) } finally { actualVerifier.fill(0) }
            if (matches) {
                val wrapped = SnapshotCryptoEngine.decryptSnapshot(Base64.decode(requireNotNull(prefs.getString(KEY_WRAPPED_VAULT_DATA_KEY, null)), Base64.NO_WRAP))
                val iv = Base64.decode(requireNotNull(prefs.getString(KEY_WRAPPED_VAULT_DATA_KEY_IV, null)), Base64.NO_WRAP)
                require(iv.size == 12 && wrapped.size == 48)
                activeVdk = unwrapVaultDataKey(wrapped, iv, computedHash)
                require(activeVdk.size == 32)
                sealArchivedNotes(appContext, activeVdk)
                check(prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).commit())
                ephemeralVaultSubKey?.fill(0)
                ephemeralVaultSubKey = activeVdk.copyOf()
                inMemoryLockoutElapsed = 0L
                return VerifyResult.Success
            } else {
                // Failure: update attempt counters and escalate lockout
                val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
                val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentAttempts)

                val lockoutDurationSec = when {
                    currentAttempts >= 10 -> 300L
                    currentAttempts in 6..9 -> 60L
                    currentAttempts == 5 -> 30L
                    else -> 0L
                }

                return if (lockoutDurationSec > 0L) {
                    inMemoryLockoutElapsed = elapsed + (lockoutDurationSec * 1000L)
                    val lockoutEnd = now + (lockoutDurationSec * 1000L)
                    editor.putLong(KEY_LOCKOUT_UNTIL, lockoutEnd).commit()
                    VerifyResult.LockedOut(lockoutDurationSec)
                } else {
                    editor.commit()
                    // clamp to 0 to prevent negative "remaining attempts" display
                    VerifyResult.Incorrect(remainingAttempts = maxOf(0, 5 - currentAttempts))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying passcode", e)
            return VerifyResult.Error("Verification error occurred. Please try again.")
        } finally {
            Arrays.fill(pinChars, '0')
            computedHash?.let { Arrays.fill(it, 0.toByte()) }
            expectedHash?.let { Arrays.fill(it, 0.toByte()) }
            activeVdk?.fill(0)
        }
    }

    /**
     * Gets the remaining lockout time in seconds, or 0 if not locked out.
     */
    fun getRemainingLockoutSeconds(context: Context): Long {
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (elapsed < inMemoryLockoutElapsed) {
            return minOf(300L, max(1L, (inMemoryLockoutElapsed - elapsed + 999L) / 1000L))
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (now < lockoutUntil) {
            val remainingSec = minOf(300L, max(1L, (lockoutUntil - now + 999L) / 1000L))
            inMemoryLockoutElapsed = elapsed + (remainingSec * 1000L)
            return remainingSec
        }
        return 0L
    }

    /**
     * Disables the passcode protection for the Archive Secret Vault.
     * Restores all encrypted archived notes back to plaintext so user notes remain accessible.
     */
    @Synchronized
    fun disablePasscode(context: Context): Boolean {
        val activeSubKey = ephemeralVaultSubKey?.copyOf()
        val appContext = context.applicationContext
        val status = getVaultStatus(context)
        if (status == VaultStatus.ENABLED && activeSubKey == null) {
            Log.e(TAG, "Cannot disable vault passcode: vault is currently locked. Unlocking is required to restore notes to plaintext.")
            return false
        }

        if (activeSubKey != null) {
            try {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO + com.focusbyrj.app.util.backup.MaintenanceGate.inheritedContext()) {
                    val noteDb = NoteDatabase.getInstance(appContext)
                    noteDb.withTransaction {
                        val targetNotes = noteDb.noteDao().getAllNotesList().filter {
                            it.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(it)
                        }
                        for (note in targetNotes) {
                            if (com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) {
                                when (val result = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.tryDecryptNotePayload(note, activeSubKey)) {
                                    is com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.DecryptionResult.Success -> {
                                        noteDb.noteDao().updateNote(result.note)
                                    }
                                    is com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.DecryptionResult.NotEncrypted -> {
                                        // Already plaintext
                                    }
                                    else -> {
                                        throw IllegalStateException("Cannot disable passcode: note ${note.id} failed decryption ($result). Preserving vault credentials to prevent permanent data loss.")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt archived notes when disabling vault passcode", e)
                return false
            } finally {
                Arrays.fill(activeSubKey, 0.toByte())
            }
        }
        lockVault()
        inMemoryLockoutElapsed = 0L
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit()
            .putString(KEY_STATUS, "disabled")
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_IV)
            .remove(KEY_KDF_TYPE)
            .remove(KEY_WRAPPED_VAULT_DATA_KEY)
            .remove(KEY_WRAPPED_VAULT_DATA_KEY_IV)
            .remove(KEY_VAULT_DATA_KEY_VERSION)
            .remove(KEY_RECOVERY_CIPHERTEXT)
            .remove(KEY_RECOVERY_IV)
            .remove(KEY_RECOVERY_SALT)
            .remove(KEY_RECOVERY_ENABLED)
            .remove(KEY_RECOVERY_PHRASE_CIPHERTEXT)
            .remove(KEY_RECOVERY_PHRASE_IV)
            .remove(KEY_RECOVERY_BACKED_UP)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
    }

    /**
     * Recovers vault access using a 12-word BIP-39 mnemonic phrase and sets a new 6-digit PIN.
     * Decrypts the stored vault subkey envelope using the mnemonic phrase, validates and re-encrypts
     * all archived notes under the new Argon2id PIN key, updates the recovery envelope with the same
     * mnemonic phrase and the new key, and unlocks the vault.
     *
     * @param context Application context.
     * @param words The 12 BIP-39 mnemonic words.
     * @param newPin The new 6-digit passcode.
     * @return True if recovery succeeded and the vault is unlocked; false otherwise.
     */
    @Synchronized
    fun recoverVaultWithMnemonic(context: Context, words: List<String>, newPin: String): Boolean {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            Log.e(TAG, "Invalid new PIN format: must be exactly 6 digits")
            return false
        }
        if (words.size != 12) {
            Log.e(TAG, "Invalid mnemonic phrase: must be exactly 12 words")
            return false
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val recCiphertextStr = prefs.getString(KEY_RECOVERY_CIPHERTEXT, null)
        val recIvStr = prefs.getString(KEY_RECOVERY_IV, null)
        val recSaltStr = prefs.getString(KEY_RECOVERY_SALT, null)

        if (recCiphertextStr == null || recIvStr == null || recSaltStr == null) {
            Log.e(TAG, "No recovery envelope found for vault")
            return false
        }

        var recoveredSubKey: ByteArray? = null
        var newDerivedHash: ByteArray? = null
        val pinChars = newPin.toCharArray()

        try {
            val recCiphertext = Base64.decode(recCiphertextStr, Base64.NO_WRAP)
            val recIv = Base64.decode(recIvStr, Base64.NO_WRAP)
            val recSalt = Base64.decode(recSaltStr, Base64.NO_WRAP)

            // 1. Decrypt vault subkey from envelope using the 12-word mnemonic
            try {
                recoveredSubKey = VaultCryptoEngine.decryptRecoveryEnvelopeWithMnemonic(
                    recCiphertext, words, recSalt, recIv
                )
            } catch (e: Exception) {
                Log.e(TAG, "Mnemonic decryption failed — incorrect phrase or corrupted envelope", e)
                return false
            }

            // 2. Derive new KEK from new PIN using Argon2id
            val newSalt = ByteArray(SALT_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
            newDerivedHash = deriveArgon2idHash(pinChars, newSalt)

            require(prefs.getString(KEY_KDF_TYPE, null) == CURRENT_KDF && prefs.contains(KEY_WRAPPED_VAULT_DATA_KEY)) {
                "Unsupported development vault format; data retained"
            }
            val finalVdk = recoveredSubKey

            // Wrap finalVdk under new PIN KEK
            val (wrappedVdk, vdkIv) = wrapVaultDataKey(finalVdk, newDerivedHash)

            // Update recovery envelope with finalVdk
            val newEnvelope = VaultCryptoEngine.createRecoveryEnvelope(finalVdk, words)

            // Encrypt recovery phrase with finalVdk
            val encryptedPhrase = encryptRecoveryPhrase(words, finalVdk)

            val verifier = pinVerifier(newDerivedHash)

            // Commit new credentials and reset lockouts
            val credentialEditor = prefs.edit()
                .putString(KEY_STATUS, "enabled")
                .putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(SnapshotCryptoEngine.encryptSnapshot(verifier), Base64.NO_WRAP))
                .remove(KEY_IV)
                .remove("enc_hash")
                .putString(KEY_KDF_TYPE, CURRENT_KDF)
                .putString(KEY_WRAPPED_VAULT_DATA_KEY, Base64.encodeToString(SnapshotCryptoEngine.encryptSnapshot(wrappedVdk), Base64.NO_WRAP))
                .putString(KEY_WRAPPED_VAULT_DATA_KEY_IV, Base64.encodeToString(vdkIv, Base64.NO_WRAP))
                .putInt(KEY_VAULT_DATA_KEY_VERSION, 1)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .putBoolean(KEY_RECOVERY_ENABLED, true)
                .putString(KEY_RECOVERY_CIPHERTEXT, Base64.encodeToString(newEnvelope.first, Base64.NO_WRAP))
                .putString(KEY_RECOVERY_IV, Base64.encodeToString(newEnvelope.second, Base64.NO_WRAP))
                .putString(KEY_RECOVERY_SALT, Base64.encodeToString(newEnvelope.third, Base64.NO_WRAP))
                .putString(KEY_RECOVERY_PHRASE_CIPHERTEXT, Base64.encodeToString(encryptedPhrase.first, Base64.NO_WRAP))
                .putString(KEY_RECOVERY_PHRASE_IV, Base64.encodeToString(encryptedPhrase.second, Base64.NO_WRAP))
                .putBoolean(KEY_RECOVERY_BACKED_UP, true)

            val committed = credentialEditor.commit()

            if (committed) {
                ephemeralVaultSubKey?.fill(0)
                ephemeralVaultSubKey = finalVdk.copyOf()
                inMemoryLockoutElapsed = 0L
                Log.i(TAG, "Vault successfully recovered and re-keyed with new PIN")
                return true
            } else {
                Log.e(TAG, "Failed to commit updated credentials to SharedPreferences")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during vault mnemonic recovery", e)
            return false
        } finally {
            Arrays.fill(pinChars, '0')
            recoveredSubKey?.let { Arrays.fill(it, 0.toByte()) }
            newDerivedHash?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Checks whether the user has confirmed backing up their 12-word recovery phrase.
     */
    fun isRecoveryPhraseBackedUp(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_RECOVERY_BACKED_UP, false)
    }

    /**
     * Marks the recovery phrase as backed up.
     */
    fun markRecoveryPhraseBackedUp(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RECOVERY_BACKED_UP, true).apply()
    }

    /**
     * Retrieves the decrypted 12-word recovery phrase for an unlocked vault.
     * Returns null if vault is locked or recovery phrase not configured.
     */
    @Synchronized
    fun getStoredRecoveryPhrase(context: Context): List<String>? {
        val activeSubKey = ephemeralVaultSubKey ?: return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getStoredRecoveryPhraseInternal(prefs, activeSubKey)
    }

    private fun getStoredRecoveryPhraseInternal(prefs: android.content.SharedPreferences, subKey: ByteArray): List<String>? {
        val cipherBase64 = prefs.getString(KEY_RECOVERY_PHRASE_CIPHERTEXT, null) ?: return null
        val ivBase64 = prefs.getString(KEY_RECOVERY_PHRASE_IV, null) ?: return null
        return try {
            val ciphertext = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            decryptRecoveryPhrase(ciphertext, iv, subKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting stored recovery phrase", e)
            null
        }
    }

    /**
     * Gets the stored recovery phrase, or if not configured, generates a new 12-word mnemonic,
     * wraps the active vault subkey in a recovery envelope, encrypts the phrase under the subkey,
     * commits it, and returns the 12 words.
     *
     * Requires the vault to be unlocked (active vault subkey in memory).
     */
    @Synchronized
    fun getOrConfigureRecoveryPhrase(context: Context): List<String>? {
        val activeSubKey = ephemeralVaultSubKey ?: return null
        val existing = getStoredRecoveryPhrase(context)
        if (existing != null && existing.size == 12) {
            return existing
        }

        val existingPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (existingPrefs.getBoolean(KEY_RECOVERY_ENABLED, false) || existingPrefs.contains(KEY_RECOVERY_CIPHERTEXT) ||
            existingPrefs.contains(KEY_RECOVERY_PHRASE_CIPHERTEXT)) return null

        // Generate fresh 12-word BIP-39 mnemonic and configure recovery envelope
        val words = VaultCryptoEngine.generate12WordMnemonic()
        val envelope = VaultCryptoEngine.createRecoveryEnvelope(activeSubKey, words)
        val encryptedPhrase = encryptRecoveryPhrase(words, activeSubKey)

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val committed = prefs.edit()
            .putBoolean(KEY_RECOVERY_ENABLED, true)
            .putString(KEY_RECOVERY_CIPHERTEXT, Base64.encodeToString(envelope.first, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_IV, Base64.encodeToString(envelope.second, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_SALT, Base64.encodeToString(envelope.third, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_PHRASE_CIPHERTEXT, Base64.encodeToString(encryptedPhrase.first, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_PHRASE_IV, Base64.encodeToString(encryptedPhrase.second, Base64.NO_WRAP))
            .putBoolean(KEY_RECOVERY_BACKED_UP, false)
            .commit()

        return if (committed) words else null
    }

    private fun derivePhraseEncryptionKey(vaultSubKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("focus_vault_recovery_phrase_v1".toByteArray(Charsets.UTF_8))
        digest.update(vaultSubKey)
        return digest.digest()
    }

    fun encryptRecoveryPhrase(phraseWords: List<String>, vaultSubKey: ByteArray): Pair<ByteArray, ByteArray> {
        val phraseText = phraseWords.joinToString(" ")
        val phraseBytes = phraseText.toByteArray(Charsets.UTF_8)
        val phraseKey = derivePhraseEncryptionKey(vaultSubKey)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(phraseKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(phraseBytes)
            Pair(ciphertext, iv)
        } finally {
            Arrays.fill(phraseBytes, 0.toByte())
            Arrays.fill(phraseKey, 0.toByte())
        }
    }

    fun decryptRecoveryPhrase(ciphertext: ByteArray, iv: ByteArray, vaultSubKey: ByteArray): List<String>? {
        val phraseKey = derivePhraseEncryptionKey(vaultSubKey)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(phraseKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            val phraseText = String(decryptedBytes, Charsets.UTF_8)
            Arrays.fill(decryptedBytes, 0.toByte())
            phraseText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt recovery phrase", e)
            null
        } finally {
            Arrays.fill(phraseKey, 0.toByte())
        }
    }

    /**
     * Verifies whether a 12-word mnemonic phrase correctly unlocks the recovery envelope
     * without modifying the stored credentials or changing the PIN.
     */
    fun verifyMnemonicOnly(context: Context, words: List<String>): Boolean {
        if (words.size != 12) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recCiphertextStr = prefs.getString(KEY_RECOVERY_CIPHERTEXT, null) ?: return false
        val recIvStr = prefs.getString(KEY_RECOVERY_IV, null) ?: return false
        val recSaltStr = prefs.getString(KEY_RECOVERY_SALT, null) ?: return false

        return try {
            val recCiphertext = Base64.decode(recCiphertextStr, Base64.NO_WRAP)
            val recIv = Base64.decode(recIvStr, Base64.NO_WRAP)
            val recSalt = Base64.decode(recSaltStr, Base64.NO_WRAP)
            val key = VaultCryptoEngine.decryptRecoveryEnvelopeWithMnemonic(
                recCiphertext, words, recSalt, recIv
            )
            Arrays.fill(key, 0.toByte())
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Marks initial passcode setup as skipped.
     */
    @Synchronized
    fun skipPasscodeSetup(context: Context): Boolean {
        // Refuse to skip if vault is already enabled; must call disablePasscode() to decrypt notes
        if (getVaultStatus(context) == VaultStatus.ENABLED) {
            Log.w(TAG, "Cannot skip passcode setup: vault is already ENABLED. Use disablePasscode() to safely restore notes.")
            return false
        }
        lockVault()
        inMemoryLockoutElapsed = 0L
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit()
            .putString(KEY_STATUS, "disabled")
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
    }

    // =========================================================================
    // CRYPTOGRAPHIC INTERNALS
    // =========================================================================

    /**
     * Derives a vault sub-key from a PIN using real Argon2id (m=32MB, t=3, p=1).
     * This is the Pattern 1 primary KDF for local vault PIN protection.
     * Replaces the fake Argon2id that was actually calling PBKDF2 under the hood.
     */
    private fun deriveArgon2idHash(pinChars: CharArray, salt: ByteArray): ByteArray {
        return Argon2idKdf.deriveKey(
            password = pinChars,
            salt = salt,
            params = Argon2idKdf.Parameters.LOGIN  // m=32MB, t=3, p=1
        )
    }

}
