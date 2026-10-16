/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

import android.content.Context
import androidx.room.withTransaction
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max

/**
 * Security manager for the Notes Secret Archive Vault.
 *
 * Features:
 * - 6-Digit Passcode Protection
 * - Argon2id Key Derivation (m=32MB, t=3, p=1) for PIN hashing — PATTERN 1 primary KDF
 * - PBKDF2-HMAC-SHA256 backward-compat fallback for existing passcodes
 * - Android KeyStore AES-256-GCM hardware-backed encryption at rest
 * - Timing-Attack Resistant Verification (MessageDigest.isEqual constant-time comparison)
 * - Brute-Force & Rate-Limiting Defense with progressive lockout escalation
 * - Ephemeral memory wiping for cryptographic hygiene
 *
 * CHANGED: setPasscode() previously called derivePbkdf2Hash() as the primary KDF.
 *          Now calls Argon2idKdf.deriveKey() (real native Argon2id) as the primary KDF.
 *          New kdf_type = "argon2id". Old PBKDF2 passcodes verified via backward-compat path.
 */
object ArchiveVaultSecurity {

    private const val TAG = "ArchiveVaultSecurity"
    const val PREFS_NAME = "focus_notes_archive_vault_security"
    const val KEY_STATUS = "vault_status" // "not_configured", "enabled", "disabled"
    private const val KEY_SALT = "enc_salt"
    private const val KEY_HASH = "enc_hash"
    private const val KEY_IV = "enc_iv"
    private const val KEY_KDF_TYPE = "kdf_type" // "argon2id" or "pbkdf2"
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
    private const val KEY_VDK_MIGRATION_PENDING = "vdk_migration_pending"
    private const val KEY_VDK_MIGRATION_OLD_KEY = "vdk_migration_old_key"
    private const val KEY_VDK_MIGRATION_OLD_KEY_IV = "vdk_migration_old_key_iv"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "focus_archive_vault_master_key"
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

    /**
     * Idempotently migrates legacy PIN-keyed notes to a stable VDK. A note may already use
     * [newVdk] after an interrupted attempt, so both keys are tried before failing closed.
     */
    private fun migrateLegacyNotesCrashSafely(context: Context, oldKey: ByteArray, newVdk: ByteArray) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val noteDb = NoteDatabase.getInstance(context.applicationContext)
            noteDb.withTransaction {
                val targets = noteDb.noteDao().getAllNotesList().filter {
                    it.isArchived || VaultPayloadEncryptor.isVaultEncrypted(it)
                }
                for (note in targets) {
                    val plaintext = if (VaultPayloadEncryptor.isVaultEncrypted(note)) {
                        when (val newResult = VaultPayloadEncryptor.tryDecryptNotePayload(note, newVdk)) {
                            is VaultPayloadEncryptor.DecryptionResult.Success -> newResult.note
                            else -> when (val oldResult = VaultPayloadEncryptor.tryDecryptNotePayload(note, oldKey)) {
                                is VaultPayloadEncryptor.DecryptionResult.Success -> oldResult.note
                                else -> throw IllegalStateException(
                                    "Note ${note.id} cannot be decrypted with either migration key (new=$newResult, old=$oldResult)"
                                )
                            }
                        }
                    } else {
                        note
                    }
                    noteDb.noteDao().updateNote(
                        VaultPayloadEncryptor.encryptNotePayload(plaintext.copy(isArchived = true), newVdk)
                    )
                }
            }
        }
    }

    // Ephemeral in-memory Vault Sub-Key cache while vault is unlocked
    @Volatile
    private var ephemeralVaultSubKey: ByteArray? = null

    // Monotonic clock lockout timestamp (immune to device clock manipulation)
    @Volatile
    private var inMemoryLockoutElapsed: Long = 0L

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
        return getVaultStatus(context) == VaultStatus.ENABLED && getActiveVaultSubKey() == null
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

            val hasExistingVdk = isChangingPasscode && prefs.contains(KEY_WRAPPED_VAULT_DATA_KEY)
            vaultDataKey = if (hasExistingVdk) {
                // WP4: Stable VDK already in place! ZERO note rewrites required!
                // oldSubKey IS the stable VDK.
                oldSubKey!!.copyOf()
            } else {
                // Brand-new vault or legacy upgrade: generate fresh 256-bit VDK
                ByteArray(32).also { SecureRandom().nextBytes(it) }
            }

            val finalVdk = vaultDataKey ?: return false

            // Wrap stable VDK with new PIN KEK (derivedHash)
            val (wrappedVdk, vdkIv) = wrapVaultDataKey(finalVdk, derivedHash)

            // Encrypt derived hash (KEK) with KeyStore master key
            val (encryptedHash, iv) = try {
                encryptWithMasterKey(derivedHash)
            } catch (e: Exception) {
                Log.e(TAG, "KeyStore encryption failed in setPasscode. Refusing to persist unencrypted credentials.", e)
                return false
            }

            // Check if there is an existing recovery phrase when changing passcode
            val existingWords = if (isChangingPasscode) {
                getStoredRecoveryPhraseInternal(prefs, oldSubKey)
            } else null

            val effectiveWords = mnemonicWords ?: existingWords

            // If mnemonic is available, create recovery envelope wrapping the stable VDK
            val recoveryEnvelope = if (effectiveWords != null && effectiveWords.size == 12) {
                try {
                    com.focusbyrj.app.util.sync.VaultCryptoEngine.createRecoveryEnvelope(finalVdk, effectiveWords)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create recovery envelope with mnemonic", e)
                    null
                }
            } else null

            val encryptedPhrase = if (effectiveWords != null && effectiveWords.size == 12) {
                try {
                    encryptRecoveryPhrase(effectiveWords, finalVdk)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to encrypt recovery phrase with VDK", e)
                    null
                }
            } else null

            val editor = prefs.edit()
                .putString(KEY_STATUS, "enabled")
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(encryptedHash, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_KDF_TYPE, "argon2id")
                .putString(KEY_WRAPPED_VAULT_DATA_KEY, Base64.encodeToString(wrappedVdk, Base64.NO_WRAP))
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

            if (isChangingPasscode && !hasExistingVdk) {
                val legacyKey = checkNotNull(oldSubKey) { "Legacy vault key is unavailable" }
                val (wrappedOldKey, oldKeyIv) = wrapVaultDataKey(legacyKey, finalVdk)
                editor.putBoolean(KEY_VDK_MIGRATION_PENDING, true)
                    .putString(KEY_VDK_MIGRATION_OLD_KEY, Base64.encodeToString(wrappedOldKey, Base64.NO_WRAP))
                    .putString(KEY_VDK_MIGRATION_OLD_KEY_IV, Base64.encodeToString(oldKeyIv, Base64.NO_WRAP))
            }

            val committed = editor.commit()
            // Set ephemeralVaultSubKey and perform note encryption only after prefs are safely on disk.
            if (committed) {
                ephemeralVaultSubKey = finalVdk.copyOf()

                if (!hasExistingVdk) {
                    if (isChangingPasscode) {
                        var notesMigrated = false
                        try {
                            migrateLegacyNotesCrashSafely(appContext, checkNotNull(oldSubKey), finalVdk)
                            notesMigrated = true
                            check(prefs.edit()
                                .putBoolean(KEY_VDK_MIGRATION_PENDING, false)
                                .remove(KEY_VDK_MIGRATION_OLD_KEY)
                                .remove(KEY_VDK_MIGRATION_OLD_KEY_IV)
                                .commit()) { "Could not durably complete passcode migration" }
                        } catch (e: Exception) {
                            // Credentials and both wrapped keys are already durable. Keep the
                            // key matching the committed database state active and resume later.
                            ephemeralVaultSubKey = if (notesMigrated) finalVdk.copyOf() else oldSubKey?.copyOf()
                            Log.w(TAG, "Passcode changed; legacy note migration will resume on next unlock", e)
                        }
                    } else {
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            val noteDb = NoteDatabase.getInstance(appContext)
                            noteDb.withTransaction {
                                // First time setting passcode (brand-new vault): encrypt any existing plaintext archived notes
                                val targetNotes = noteDb.noteDao().getAllNotesList().filter { it.isArchived }
                                for (note in targetNotes) {
                                    if (!com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) {
                                        val encrypted = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(note, finalVdk)
                                        noteDb.noteDao().updateNote(encrypted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return committed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set vault passcode", e)
            return false
        } finally {
            Arrays.fill(pinChars, '0')
            derivedHash?.let { Arrays.fill(it, 0.toByte()) }
            oldSubKey?.let { Arrays.fill(it, 0.toByte()) }
            vaultDataKey?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Verifies the 6-digit passcode against the encrypted hash (Argon2id with PBKDF2 backward compatibility).
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

        // 3. Load credentials
        val saltBase64 = prefs.getString(KEY_SALT, null)
        val hashBase64 = prefs.getString(KEY_HASH, null)
        val ivBase64 = prefs.getString(KEY_IV, null)
        val kdfType = prefs.getString(KEY_KDF_TYPE, "pbkdf2")

        if (saltBase64 == null || hashBase64 == null) {
            return VerifyResult.Error("Vault passcode is not configured.")
        }

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val storedHashPayload = Base64.decode(hashBase64, Base64.NO_WRAP)
        val iv = if (ivBase64 != null) Base64.decode(ivBase64, Base64.NO_WRAP) else ByteArray(0)

        // Decrypt expected hash if KeyStore IV exists
        val expectedHash = if (iv.isNotEmpty()) {
            var decrypted: ByteArray? = null
            var lastEx: Exception? = null
            for (attempt in 1..3) {
                try {
                    decrypted = decryptWithMasterKey(storedHashPayload, iv)
                    if (decrypted != null) break
                } catch (e: Exception) {
                    lastEx = e
                    Log.w(TAG, "KeyStore decryption attempt $attempt failed, retrying...", e)
                    try { Thread.sleep(50L * attempt) } catch (_: InterruptedException) {}
                }
            }
            if (decrypted == null) {
                // Refuse to fall back to raw ciphertext on KeyStore failure.
                // Comparing against ciphertext guarantees false PIN failure and unfair user lockout.
                Log.e(TAG, "KeyStore hardware security failure: unable to decrypt vault hash.", lastEx)
                return VerifyResult.Error("Hardware security error: unable to securely retrieve vault credentials. Please try again.")
            }
            decrypted
        } else {
            storedHashPayload
        }

        val pinChars = inputPin.toCharArray()
        var computedHash: ByteArray? = null
        try {
            // Primary: Argon2id (Pattern 1, kdf_type = "argon2id" or any non-pbkdf2 value)
            computedHash = if (kdfType == "pbkdf2") {
                derivePbkdf2Hash(pinChars, salt)
            } else {
                deriveArgon2idHash(pinChars, salt)
            }

            // Constant-time comparison to prevent timing attacks
            var isMatch = MessageDigest.isEqual(computedHash, expectedHash)

            // Backward-compat: if Argon2id failed, try PBKDF2 for old passcodes stored as 'argon2id'
            // (This handles the case where the old fake 'argon2id' was actually PBKDF2)
            if (!isMatch && kdfType != "pbkdf2") {
                val pbkdf2Hash = derivePbkdf2Hash(pinChars, salt)
                if (MessageDigest.isEqual(pbkdf2Hash, expectedHash)) {
                    isMatch = true
                    // Auto-upgrade: re-hash with real Argon2id and update stored hash
                    val realArgon2idHash = deriveArgon2idHash(pinChars, salt)
                    var autoUpgradeSuccess = false
                    try {
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            val noteDb = NoteDatabase.getInstance(appContext)
                            noteDb.withTransaction {
                                val targetNotes = noteDb.noteDao().getAllNotesList().filter {
                                    it.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(it)
                                }
                                for (note in targetNotes) {
                                    if (com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) {
                                        // Use tryDecryptNotePayload to ensure auto-upgrade aborts on failure
                                        val decrypted = when (val res = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.tryDecryptNotePayload(note, pbkdf2Hash)) {
                                            is com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.DecryptionResult.Success -> res.note
                                            else -> throw IllegalStateException("Note ${note.id} failed decryption during auto-upgrade ($res)")
                                        }
                                        val reEncrypted = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(decrypted, realArgon2idHash)
                                        noteDb.noteDao().updateNote(reEncrypted)
                                    }
                                }
                            }
                        }
                        autoUpgradeSuccess = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to re-encrypt archived notes during Argon2id auto-upgrade", e)
                    }

                    if (autoUpgradeSuccess) {
                        // Re-wrap recovery envelope under realArgon2idHash so mnemonic recovery remains valid
                        val existingWords = getStoredRecoveryPhraseInternal(prefs, pbkdf2Hash)
                        val recoveryEnvelope = if (existingWords != null && existingWords.size == 12) {
                            try {
                                com.focusbyrj.app.util.sync.VaultCryptoEngine.createRecoveryEnvelope(realArgon2idHash, existingWords)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to re-wrap recovery envelope with realArgon2idHash during auto-upgrade", e)
                                null
                            }
                        } else null

                        val encryptedPhrase = if (existingWords != null && existingWords.size == 12) {
                            try {
                                encryptRecoveryPhrase(existingWords, realArgon2idHash)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to re-encrypt recovery phrase during auto-upgrade", e)
                                null
                            }
                        } else null

                        val keyStoreResult = try {
                            encryptWithMasterKey(realArgon2idHash)
                        } catch (e: Exception) {
                            Log.e(TAG, "KeyStore encryption failed during Argon2id auto-upgrade. Aborting.", e)
                            null
                        }

                        if (keyStoreResult != null) {
                            val (newEncHash, newIv) = keyStoreResult
                            val editor = prefs.edit()
                                .putString(KEY_HASH, Base64.encodeToString(newEncHash, Base64.NO_WRAP))
                                .putString(KEY_IV, Base64.encodeToString(newIv, Base64.NO_WRAP))
                                .putString(KEY_KDF_TYPE, "argon2id")

                            if (recoveryEnvelope != null && encryptedPhrase != null) {
                                editor.putBoolean(KEY_RECOVERY_ENABLED, true)
                                    .putString(KEY_RECOVERY_CIPHERTEXT, Base64.encodeToString(recoveryEnvelope.first, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_IV, Base64.encodeToString(recoveryEnvelope.second, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_SALT, Base64.encodeToString(recoveryEnvelope.third, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_PHRASE_CIPHERTEXT, Base64.encodeToString(encryptedPhrase.first, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_PHRASE_IV, Base64.encodeToString(encryptedPhrase.second, Base64.NO_WRAP))
                            }

                            val committed = editor.commit()

                            if (committed) {
                                computedHash?.let { Arrays.fill(it, 0.toByte()) }
                                computedHash = realArgon2idHash
                            } else {
                                Arrays.fill(realArgon2idHash, 0.toByte())
                            }
                        } else {
                            Arrays.fill(realArgon2idHash, 0.toByte())
                        }
                    } else {
                        Arrays.fill(realArgon2idHash, 0.toByte())
                    }
                }
                Arrays.fill(pbkdf2Hash, 0.toByte())
            } else if (isMatch && kdfType == "pbkdf2") {
                // Auto-upgrade legacy pbkdf2 vaults to real Argon2id on successful unlock
                val realArgon2idHash = deriveArgon2idHash(pinChars, salt)
                var autoUpgradeSuccess = false
                try {
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        val noteDb = NoteDatabase.getInstance(appContext)
                        noteDb.withTransaction {
                            val targetNotes = noteDb.noteDao().getAllNotesList().filter {
                                it.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(it)
                            }
                            for (note in targetNotes) {
                                if (com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) {
                                    // Use tryDecryptNotePayload to ensure auto-upgrade aborts on failure
                                    val decrypted = when (val res = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.tryDecryptNotePayload(note, computedHash)) {
                                        is com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.DecryptionResult.Success -> res.note
                                        else -> throw IllegalStateException("Note ${note.id} failed decryption during auto-upgrade ($res)")
                                    }
                                    val reEncrypted = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(decrypted, realArgon2idHash)
                                    noteDb.noteDao().updateNote(reEncrypted)
                                }
                            }
                        }
                    }
                    autoUpgradeSuccess = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-upgrade legacy PBKDF2 vault to Argon2id", e)
                }

                if (autoUpgradeSuccess) {
                    // Re-wrap recovery envelope under realArgon2idHash so mnemonic recovery remains valid
                    val existingWords = computedHash?.let { getStoredRecoveryPhraseInternal(prefs, it) }
                    val recoveryEnvelope = if (existingWords != null && existingWords.size == 12) {
                        try {
                            com.focusbyrj.app.util.sync.VaultCryptoEngine.createRecoveryEnvelope(realArgon2idHash, existingWords)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to re-wrap recovery envelope with realArgon2idHash during auto-upgrade", e)
                            null
                        }
                    } else null

                    val encryptedPhrase = if (existingWords != null && existingWords.size == 12) {
                        try {
                            encryptRecoveryPhrase(existingWords, realArgon2idHash)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to re-encrypt recovery phrase during auto-upgrade", e)
                            null
                        }
                    } else null

                    val keyStoreResult = try {
                        encryptWithMasterKey(realArgon2idHash)
                    } catch (e: Exception) {
                        Log.e(TAG, "KeyStore encryption failed during legacy KDF auto-upgrade. Aborting.", e)
                        null
                    }

                    if (keyStoreResult != null) {
                        val (newEncHash, newIv) = keyStoreResult
                        val editor = prefs.edit()
                            .putString(KEY_HASH, Base64.encodeToString(newEncHash, Base64.NO_WRAP))
                            .putString(KEY_IV, Base64.encodeToString(newIv, Base64.NO_WRAP))
                            .putString(KEY_KDF_TYPE, "argon2id")

                        if (recoveryEnvelope != null && encryptedPhrase != null) {
                            editor.putBoolean(KEY_RECOVERY_ENABLED, true)
                                .putString(KEY_RECOVERY_CIPHERTEXT, Base64.encodeToString(recoveryEnvelope.first, Base64.NO_WRAP))
                                .putString(KEY_RECOVERY_IV, Base64.encodeToString(recoveryEnvelope.second, Base64.NO_WRAP))
                                .putString(KEY_RECOVERY_SALT, Base64.encodeToString(recoveryEnvelope.third, Base64.NO_WRAP))
                                .putString(KEY_RECOVERY_PHRASE_CIPHERTEXT, Base64.encodeToString(encryptedPhrase.first, Base64.NO_WRAP))
                                .putString(KEY_RECOVERY_PHRASE_IV, Base64.encodeToString(encryptedPhrase.second, Base64.NO_WRAP))
                        }

                        val committed = editor.commit()

                        if (committed) {
                            computedHash?.let { Arrays.fill(it, 0.toByte()) }
                            computedHash = realArgon2idHash
                        } else {
                            Arrays.fill(realArgon2idHash, 0.toByte())
                        }
                    } else {
                        Arrays.fill(realArgon2idHash, 0.toByte())
                    }
                } else {
                    Arrays.fill(realArgon2idHash, 0.toByte())
                }
            }

            if (isMatch) {
                // If vault has stable wrapped Vault Data Key (VDK), unwrap it using PIN KEK (computedHash)
                val hasWrappedVdk = prefs.contains(KEY_WRAPPED_VAULT_DATA_KEY)
                val activeVdk: ByteArray? = if (hasWrappedVdk) {
                    try {
                        val wrappedVdkStr = prefs.getString(KEY_WRAPPED_VAULT_DATA_KEY, null)
                        val vdkIvStr = prefs.getString(KEY_WRAPPED_VAULT_DATA_KEY_IV, null)
                        if (wrappedVdkStr != null && vdkIvStr != null && computedHash != null) {
                            val wrappedVdk = Base64.decode(wrappedVdkStr, Base64.NO_WRAP)
                            val vdkIv = Base64.decode(vdkIvStr, Base64.NO_WRAP)
                            val unwrapped = unwrapVaultDataKey(wrappedVdk, vdkIv, computedHash)
                            if (prefs.getBoolean(KEY_VDK_MIGRATION_PENDING, false)) {
                                val oldWrapped = Base64.decode(
                                    prefs.getString(KEY_VDK_MIGRATION_OLD_KEY, null)
                                        ?: throw SecurityException("Missing migration recovery key"),
                                    Base64.NO_WRAP
                                )
                                val oldIv = Base64.decode(
                                    prefs.getString(KEY_VDK_MIGRATION_OLD_KEY_IV, null)
                                        ?: throw SecurityException("Missing migration recovery-key IV"),
                                    Base64.NO_WRAP
                                )
                                val oldKey = unwrapVaultDataKey(oldWrapped, oldIv, unwrapped)
                                migrateLegacyNotesCrashSafely(appContext, oldKey, unwrapped)
                                Arrays.fill(oldKey, 0.toByte())
                                check(prefs.edit()
                                    .putBoolean(KEY_VDK_MIGRATION_PENDING, false)
                                    .remove(KEY_VDK_MIGRATION_OLD_KEY)
                                    .remove(KEY_VDK_MIGRATION_OLD_KEY_IV)
                                    .commit()) {
                                    "Could not durably complete vault-key migration"
                                }
                            }
                            unwrapped
                        } else null
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to unwrap Vault Data Key with verified PIN KEK", e)
                        null
                    }
                } else if (computedHash != null) {
                    // Legacy vault upgrade. Persist the recoverable key envelope and a journal
                    // marker before touching ciphertext. The migration itself is idempotent.
                    val newVdk = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    var notesMigrated = false
                    try {
                        val (wrappedVdk, vdkIv) = wrapVaultDataKey(newVdk, computedHash)
                        val (wrappedOldKey, oldKeyIv) = wrapVaultDataKey(computedHash, newVdk)
                        val vdkEditor = prefs.edit()
                            .putString(KEY_WRAPPED_VAULT_DATA_KEY, Base64.encodeToString(wrappedVdk, Base64.NO_WRAP))
                            .putString(KEY_WRAPPED_VAULT_DATA_KEY_IV, Base64.encodeToString(vdkIv, Base64.NO_WRAP))
                            .putInt(KEY_VAULT_DATA_KEY_VERSION, 1)
                            .putBoolean(KEY_VDK_MIGRATION_PENDING, true)
                            .putString(KEY_VDK_MIGRATION_OLD_KEY, Base64.encodeToString(wrappedOldKey, Base64.NO_WRAP))
                            .putString(KEY_VDK_MIGRATION_OLD_KEY_IV, Base64.encodeToString(oldKeyIv, Base64.NO_WRAP))

                        val existingWords = getStoredRecoveryPhraseInternal(prefs, computedHash)
                        if (existingWords != null && existingWords.size == 12) {
                            try {
                                val recEnv = com.focusbyrj.app.util.sync.VaultCryptoEngine.createRecoveryEnvelope(newVdk, existingWords)
                                val encPhrase = encryptRecoveryPhrase(existingWords, newVdk)
                                vdkEditor.putString(KEY_RECOVERY_CIPHERTEXT, Base64.encodeToString(recEnv.first, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_IV, Base64.encodeToString(recEnv.second, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_SALT, Base64.encodeToString(recEnv.third, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_PHRASE_CIPHERTEXT, Base64.encodeToString(encPhrase.first, Base64.NO_WRAP))
                                    .putString(KEY_RECOVERY_PHRASE_IV, Base64.encodeToString(encPhrase.second, Base64.NO_WRAP))
                            } catch (_: Exception) {}
                        }
                        check(vdkEditor.commit()) { "Could not persist vault-key migration journal" }
                        migrateLegacyNotesCrashSafely(appContext, computedHash, newVdk)
                        notesMigrated = true
                        check(prefs.edit()
                            .putBoolean(KEY_VDK_MIGRATION_PENDING, false)
                            .remove(KEY_VDK_MIGRATION_OLD_KEY)
                            .remove(KEY_VDK_MIGRATION_OLD_KEY_IV)
                            .commit()) {
                            "Could not durably complete vault-key migration"
                        }
                        newVdk
                    } catch (e: Exception) {
                        // The durable pending marker allows the next successful PIN verification
                        // to resume safely whether zero or all note updates committed.
                        Log.w(TAG, "Legacy note migration to VDK failed/deferred", e)
                        if (notesMigrated) newVdk else computedHash.copyOf()
                    }
                } else null

                if (hasWrappedVdk && activeVdk == null) {
                    ephemeralVaultSubKey = null
                    return VerifyResult.Error(
                        "Vault key migration or recovery could not be completed safely. No private data was opened."
                    )
                }
                ephemeralVaultSubKey = activeVdk?.copyOf() ?: computedHash?.copyOf()
                inMemoryLockoutElapsed = 0L

                // Auto-encrypt any plaintext notes found in the vault upon successful unlock
                try {
                    val subKey = ephemeralVaultSubKey
                    if (subKey != null) {
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            val noteDb = NoteDatabase.getInstance(appContext)
                            noteDb.withTransaction {
                                val archivedNotes = noteDb.noteDao().getArchivedNotesSync()
                                for (note in archivedNotes) {
                                    if (!com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) {
                                        val encrypted = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(note, subKey)
                                        noteDb.noteDao().updateNote(encrypted)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-encrypt plaintext archived notes on vault unlock", e)
                }

                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, 0L)
                    .commit()
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
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
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

            val hasExistingVdk = prefs.contains(KEY_WRAPPED_VAULT_DATA_KEY)
            val finalVdk = if (hasExistingVdk) {
                // Stable VDK architecture: recoveredSubKey IS the stable VDK!
                // ZERO note rewrites required!
                recoveredSubKey.copyOf()
            } else {
                // The old recovery key remains wrapped in a durable migration journal below.
                // This makes the subsequent database rewrite resumable after process death.
                ByteArray(32).also { SecureRandom().nextBytes(it) }
            }

            // Wrap finalVdk under new PIN KEK
            val (wrappedVdk, vdkIv) = wrapVaultDataKey(finalVdk, newDerivedHash)

            // Update recovery envelope with finalVdk
            val newEnvelope = VaultCryptoEngine.createRecoveryEnvelope(finalVdk, words)

            // Encrypt recovery phrase with finalVdk
            val encryptedPhrase = encryptRecoveryPhrase(words, finalVdk)

            // Encrypt new PIN KEK with KeyStore master key
            val (encryptedHash, iv) = try {
                encryptWithMasterKey(newDerivedHash)
            } catch (e: Exception) {
                Log.e(TAG, "KeyStore encryption failed during recovery. Failing closed.", e)
                return false
            }

            // Commit new credentials and reset lockouts
            val credentialEditor = prefs.edit()
                .putString(KEY_STATUS, "enabled")
                .putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(encryptedHash, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_KDF_TYPE, "argon2id")
                .putString(KEY_WRAPPED_VAULT_DATA_KEY, Base64.encodeToString(wrappedVdk, Base64.NO_WRAP))
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

            if (!hasExistingVdk) {
                val (wrappedOldKey, oldKeyIv) = wrapVaultDataKey(recoveredSubKey, finalVdk)
                credentialEditor
                    .putBoolean(KEY_VDK_MIGRATION_PENDING, true)
                    .putString(KEY_VDK_MIGRATION_OLD_KEY, Base64.encodeToString(wrappedOldKey, Base64.NO_WRAP))
                    .putString(KEY_VDK_MIGRATION_OLD_KEY_IV, Base64.encodeToString(oldKeyIv, Base64.NO_WRAP))
            }

            val committed = credentialEditor.commit()

            if (committed) {
                if (!hasExistingVdk) {
                    var notesMigrated = false
                    try {
                        migrateLegacyNotesCrashSafely(appContext, recoveredSubKey, finalVdk)
                        notesMigrated = true
                        check(prefs.edit()
                            .putBoolean(KEY_VDK_MIGRATION_PENDING, false)
                            .remove(KEY_VDK_MIGRATION_OLD_KEY)
                            .remove(KEY_VDK_MIGRATION_OLD_KEY_IV)
                            .commit()) { "Could not durably complete recovered vault migration" }
                    } catch (e: Exception) {
                        ephemeralVaultSubKey = if (notesMigrated) finalVdk.copyOf() else recoveredSubKey.copyOf()
                        Log.w(TAG, "Vault recovered; legacy note migration will resume on next unlock", e)
                        inMemoryLockoutElapsed = 0L
                        return true
                    }
                }
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
    fun getOrConfigureRecoveryPhrase(context: Context): List<String>? {
        val activeSubKey = ephemeralVaultSubKey ?: return null
        val existing = getStoredRecoveryPhrase(context)
        if (existing != null && existing.size == 12) {
            return existing
        }

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

    /**
     * Legacy PBKDF2 hash derivation — used only for backward compatibility when verifying
     * passcodes that were set before the Pattern 1 migration.
     */
    private fun derivePbkdf2Hash(pinChars: CharArray, salt: ByteArray): ByteArray {
        val keySpec = javax.crypto.spec.PBEKeySpec(pinChars, salt, 100_000, KEY_LENGTH_BITS)
        return try {
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    private val isUnitTestEnvironment: Boolean by lazy {
        try {
            android.os.Build.FINGERPRINT.startsWith("robolectric") ||
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun getMasterKeyForEncryption(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
                throw SecurityException("Archive vault master key exists in AndroidKeyStore but could not be loaded as SecretKeyEntry. Refusing to overwrite key to prevent permanent data loss.")
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            if (e is SecurityException) throw e
            if (isUnitTestEnvironment) {
                Log.w(TAG, "AndroidKeyStore is unavailable in unit test environment; using test software key.")
                return getLegacyFallbackKey()
            }
            Log.e(TAG, "AndroidKeyStore is unavailable for archive vault master key generation. Refusing to use static software key for new writes.", e)
            throw SecurityException("Failed to generate hardware-backed archive vault master key in AndroidKeyStore", e)
        }
    }

    private fun getMasterKeyForDecryption(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve archive vault master key from AndroidKeyStore", e)
            null
        }
    }

    internal fun getLegacyFallbackKey(): SecretKey {
        val fallbackSeed = java.security.MessageDigest.getInstance("SHA-256")
            .digest("focus_vault_master_software_seed_v1".toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(fallbackSeed, "AES")
    }

    private fun encryptWithMasterKey(data: ByteArray): Pair<ByteArray, ByteArray> {
        val masterKey = getMasterKeyForEncryption()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = checkNotNull(cipher.iv) { "Cipher failed to generate IV for master key encryption" }
        val encrypted = cipher.doFinal(data)
        return Pair(encrypted, iv)
    }

    internal fun decryptWithMasterKey(encryptedData: ByteArray, iv: ByteArray): ByteArray {
        // 1. Primary: KeyStore hardware master key
        try {
            val masterKey = getMasterKeyForDecryption()
            if (masterKey != null) {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
                return cipher.doFinal(encryptedData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "KeyStore master key decryption failed, attempting legacy compatibility adapter...", e)
        }

        // 2. Compatibility Adapter: Legacy software fallback key (read-only)
        val legacyKey = getLegacyFallbackKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, legacyKey, spec)
        return cipher.doFinal(encryptedData)
    }
}
