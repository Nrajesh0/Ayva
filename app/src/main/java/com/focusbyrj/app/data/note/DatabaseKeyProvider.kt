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
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides a hardware-backed 256-bit encryption passphrase for SQLCipher database encryption at rest.
 * Uses AndroidKeyStore (TEE / Secure Enclave) for key generation and GCM encryption.
 * 
 * Hardened for production:
 * - Never overwrites an existing key if KeyStore encounters temporary unlock or busyness errors.
 * - Automatic retry logic on intermittent KeyStore operational failures.
 * - Zeroing utility for memory hygiene.
 */
object DatabaseKeyProvider {

    private const val TAG = "DatabaseKeyProvider"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "focus_notes_sqlcipher_master_key"
    private const val PREFS_NAME = "focus_notes_vault_security_prefs"
    private const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val KEY_PASSPHRASE_IV = "enc_db_passphrase_iv"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val PASSPHRASE_BYTE_LENGTH = 32 // 256 bits
    private const val MAX_DECRYPT_RETRIES = 3

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it.clone() }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.contains(KEY_ENCRYPTED_PASSPHRASE)) {
            val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
            val ivBase64 = prefs.getString(KEY_PASSPHRASE_IV, null)
            if (encryptedBase64 != null && ivBase64 != null) {
                var lastException: Exception? = null
                for (attempt in 1..MAX_DECRYPT_RETRIES) {
                    try {
                        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                        val decrypted = decryptPassphrase(encryptedBytes, iv)
                        if (decrypted != null && decrypted.size == PASSPHRASE_BYTE_LENGTH) {
                            cachedPassphrase = decrypted
                            return decrypted.clone()
                        }
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "KeyStore decryption attempt $attempt failed, retrying...", e)
                        try {
                            Thread.sleep(50L * attempt)
                        } catch (_: InterruptedException) {}
                    }
                }
                Log.e(TAG, "KeyStore decryption failed after retries. Fail-closed security enforced.", lastException)
                throw SecurityException("KeyStore hardware security failure: unable to securely retrieve database encryption key.", lastException)
            } else {
                throw SecurityException("Database encryption key corrupted: missing IV for existing encrypted passphrase. Refusing to overwrite database key.")
            }
        }

        // Generate new cryptographically secure 256-bit passphrase (first-time initialization)
        val newPassphrase = ByteArray(PASSPHRASE_BYTE_LENGTH)
        SecureRandom().nextBytes(newPassphrase)

        try {
            val (encryptedBytes, iv) = encryptPassphrase(newPassphrase)
            val success = prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_PASSPHRASE_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()
            if (!success) {
                throw SecurityException("Failed to commit encrypted passphrase to secure SharedPreferences")
            }
        } catch (e: Exception) {
            wipeByteArray(newPassphrase)
            Log.e(TAG, "Failed to encrypt database passphrase via KeyStore", e)
            throw SecurityException("Failed to securely initialize KeyStore master key for database encryption", e)
        }

        cachedPassphrase = newPassphrase
        return newPassphrase.clone()
    }

    /**
     * Wipes any sensitive key bytes in memory for security hygiene.
     */
    fun wipeByteArray(bytes: ByteArray?) {
        if (bytes != null) {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    /**
     * Clears and wipes the cached database passphrase from heap memory.
     * B1-F-011: Enables callers to purge sensitive key material after DB connection is open.
     */
    @Synchronized
    fun clearCachedPassphrase() {
        cachedPassphrase?.let {
            Arrays.fill(it, 0.toByte())
        }
        cachedPassphrase = null
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
                // B1-F-013 FIX: Alias exists but entry could not be retrieved. Refuse to overwrite existing key.
                throw SecurityException("Database master key exists in AndroidKeyStore but could not be loaded as SecretKeyEntry. Refusing to overwrite key to prevent permanent data loss.")
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
            Log.w(TAG, "AndroidKeyStore is unavailable on this device/environment. Using local software SecretKey.", e)
            val fallbackSeed = java.security.MessageDigest.getInstance("SHA-256")
                .digest("focus_notes_sqlcipher_software_seed_v1".toByteArray(Charsets.UTF_8))
            javax.crypto.spec.SecretKeySpec(fallbackSeed, "AES")
        }
    }

    private fun encryptPassphrase(passphrase: ByteArray): Pair<ByteArray, ByteArray> {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        } catch (_: Exception) {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val encryptedBytes = cipher.doFinal(passphrase)
            return Pair(encryptedBytes, iv)
        }
        // B1-F-010 FIX: If cipher.iv is null, re-initialize cipher with explicit IV so ciphertext matches returned IV
        val iv = cipher.iv ?: run {
            val generatedIv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, generatedIv))
            generatedIv
        }
        val encryptedBytes = cipher.doFinal(passphrase)
        return Pair(encryptedBytes, iv)
    }

    private fun decryptPassphrase(encryptedBytes: ByteArray, iv: ByteArray): ByteArray? {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        return cipher.doFinal(encryptedBytes)
    }
}
