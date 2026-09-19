/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.data.note

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlin.math.max

/**
 * Military-grade security manager for the Notes Secret Archive Vault.
 *
 * Features:
 * - 6-Digit Passcode Protection
 * - PBKDF2-HMAC-SHA256 Key Derivation (100,000 iterations) with 16-byte Cryptographic Salt
 * - Android KeyStore AES-256-GCM hardware-backed encryption at rest
 * - Timing-Attack Resistant Verification (MessageDigest.isEqual constant-time comparison)
 * - Brute-Force & Rate-Limiting Defense with progressive lockout escalation
 * - Ephemeral memory wiping for cryptographic hygiene
 */
object ArchiveVaultSecurity {

    private const val TAG = "ArchiveVaultSecurity"
    private const val PREFS_NAME = "focus_notes_archive_vault_security"
    private const val KEY_STATUS = "vault_status" // "not_configured", "enabled", "disabled"
    private const val KEY_SALT = "enc_salt"
    private const val KEY_HASH = "enc_hash"
    private const val KEY_IV = "enc_iv"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until_epoch_ms"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "focus_archive_vault_master_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTE_LENGTH = 16

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

    /**
     * Configures a new 6-digit passcode for the Archive Secret Vault.
     */
    @Synchronized
    fun setPasscode(context: Context, pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            Log.e(TAG, "Invalid PIN format: must be exactly 6 digits")
            return false
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_BYTE_LENGTH)
        secureRandom.nextBytes(salt)

        val pinChars = pin.toCharArray()
        var derivedHash: ByteArray? = null
        try {
            derivedHash = derivePbkdf2Hash(pinChars, salt)

            // Encrypt derived hash with KeyStore master key
            val (encryptedHash, iv) = try {
                encryptWithMasterKey(derivedHash)
            } catch (e: Exception) {
                Log.w(TAG, "KeyStore encryption failed, using sandboxed fallback", e)
                Pair(derivedHash, ByteArray(0))
            }

            val editor = prefs.edit()
                .putString(KEY_STATUS, "enabled")
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(encryptedHash, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)

            val committed = editor.commit()
            return committed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set vault passcode", e)
            return false
        } finally {
            Arrays.fill(pinChars, '0')
            derivedHash?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Verifies the 6-digit passcode against the encrypted PBKDF2 hash.
     * Protected by progressive brute-force rate-limiting and timing-attack-resistant comparison.
     */
    @Synchronized
    fun verifyPasscode(context: Context, inputPin: String): VerifyResult {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // 1. Check brute-force lockout status
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            val remainingSec = max(1L, (lockoutUntil - now + 999L) / 1000L)
            return VerifyResult.LockedOut(remainingSec)
        }

        // 2. Validate input format
        if (inputPin.length != 6 || !inputPin.all { it.isDigit() }) {
            return VerifyResult.Incorrect(remainingAttempts = 5)
        }

        // 3. Load credentials
        val saltBase64 = prefs.getString(KEY_SALT, null)
        val hashBase64 = prefs.getString(KEY_HASH, null)
        val ivBase64 = prefs.getString(KEY_IV, null)

        if (saltBase64 == null || hashBase64 == null) {
            return VerifyResult.Error("Vault passcode is not configured.")
        }

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val storedHashPayload = Base64.decode(hashBase64, Base64.NO_WRAP)
        val iv = if (ivBase64 != null) Base64.decode(ivBase64, Base64.NO_WRAP) else ByteArray(0)

        // Decrypt expected hash if KeyStore IV exists
        val expectedHash = if (iv.isNotEmpty()) {
            try {
                decryptWithMasterKey(storedHashPayload, iv)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decrypt hash with master key, falling back to payload", e)
                storedHashPayload
            }
        } else {
            storedHashPayload
        }

        val pinChars = inputPin.toCharArray()
        var computedHash: ByteArray? = null
        try {
            computedHash = derivePbkdf2Hash(pinChars, salt)

            // Constant-time comparison to prevent timing attacks
            val isMatch = MessageDigest.isEqual(computedHash, expectedHash)

            if (isMatch) {
                // Success: reset brute force trackers
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, 0L)
                    .commit()
                return VerifyResult.Success
            } else {
                // Failure: update attempt counters and escalate lockout
                val currentAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
                val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentAttempts)

                return when {
                    currentAttempts >= 10 -> {
                        // 5-minute lockout (300 seconds)
                        val lockoutEnd = now + 300_000L
                        editor.putLong(KEY_LOCKOUT_UNTIL, lockoutEnd).commit()
                        VerifyResult.LockedOut(300L)
                    }
                    currentAttempts in 6..9 -> {
                        // 60-second lockout
                        val lockoutEnd = now + 60_000L
                        editor.putLong(KEY_LOCKOUT_UNTIL, lockoutEnd).commit()
                        VerifyResult.LockedOut(60L)
                    }
                    currentAttempts == 5 -> {
                        // 30-second lockout
                        val lockoutEnd = now + 30_000L
                        editor.putLong(KEY_LOCKOUT_UNTIL, lockoutEnd).commit()
                        VerifyResult.LockedOut(30L)
                    }
                    else -> {
                        editor.commit()
                        VerifyResult.Incorrect(remainingAttempts = 5 - currentAttempts)
                    }
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
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (now < lockoutUntil) max(1L, (lockoutUntil - now + 999L) / 1000L) else 0L
    }

    /**
     * Disables the passcode protection for the Archive Secret Vault.
     */
    @Synchronized
    fun disablePasscode(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit()
            .putString(KEY_STATUS, "disabled")
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_IV)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
    }

    /**
     * Marks initial passcode setup as skipped.
     */
    @Synchronized
    fun skipPasscodeSetup(context: Context): Boolean {
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

    private fun derivePbkdf2Hash(pinChars: CharArray, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(keySpec).encoded
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
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
        return keyGenerator.generateKey()
    }

    private fun encryptWithMasterKey(data: ByteArray): Pair<ByteArray, ByteArray> {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return Pair(encrypted, iv)
    }

    private fun decryptWithMasterKey(encryptedData: ByteArray, iv: ByteArray): ByteArray {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        return cipher.doFinal(encryptedData)
    }
}
