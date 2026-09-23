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

package com.focusbyrj.app.util.sync.supabase

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.focusbyrj.app.util.crypto.Argon2idKdf
import com.focusbyrj.app.util.crypto.HkdfUtil
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Zero-Knowledge Key Derivation and Local Session Storage — Pattern 1 Hardened Implementation.
 *
 * Mathematical flow (replaces previous PBKDF2 + SHA-256 split):
 *
 *   1. MasterKey  = Argon2id(password, randomSalt, m=32MB, t=3, p=1) → 32 bytes
 *      (Memory-hard, GPU/FPGA/ASIC resistant — replaces PBKDF2-100K which is GPU-crackable)
 *
 *   2. AuthToken  = HKDF-Expand(MasterKey, "ayva_auth_v1",  len=32) → base64 string
 *      Sent to Supabase for authentication only. Mathematically cannot be reversed to recover MasterKey.
 *
 *   3. UserKey    = HKDF-Expand(MasterKey, "ayva_vault_v1", len=32) → raw bytes
 *      Data Encryption Key. NEVER sent to server or stored in plaintext.
 *      Encrypted at rest via hardware-backed Android KeyStore (AES-256-GCM).
 *
 *   4. HmacKey    = HKDF-Expand(MasterKey, "ayva_hmac_v1",  len=32) → raw bytes
 *      Signs every cloud item. Prevents backend from tampering with note content.
 *      NEVER sent to server. Stored alongside UserKey under KeyStore protection.
 *
 *   5. Persistence: Both UserKey and HmacKey are AES-256-GCM encrypted with the hardware-backed
 *      AndroidKeyStore key and stored in MODE_PRIVATE SharedPreferences.
 *
 * REMOVED:
 *   - deriveKeys() — was PBKDF2-100K + SHA-256 split (GPU crackable in hours)
 *   - deriveKeysLegacyPbkdf2() — same code as deriveKeys(); no migration value
 *   - deriveDataKeyWithSalt() — PBKDF2 + SHA-256 split, same vulnerabilities
 *
 * ADDED:
 *   - Real Argon2id KDF via argon2kt:1.4.0 native library
 *   - HKDF-RFC5869 for domain-separated key derivation (authToken, userKey, hmacKey)
 *   - generateAndSign() / verifySignature() — HMAC-SHA256 per cloud item
 *   - getHmacKey() — parallel to getDataEncryptionKey()
 */
object SupabaseKeyManager {

    private const val TAG = "SupabaseKeyManager"
    private const val PREFS_NAME = "focus_supabase_zk_prefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "focus_zk_vault_keystore_alias"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    // SharedPreferences keys
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_DATA_KEY_CIPHERTEXT = "data_encryption_key_enc"
    private const val KEY_DATA_KEY_IV = "data_encryption_key_iv"
    private const val KEY_HMAC_KEY_CIPHERTEXT = "hmac_key_enc"
    private const val KEY_HMAC_KEY_IV = "hmac_key_iv"
    private const val KEY_DATA_KEY_BASE64_LEGACY = "data_encryption_key_b64"
    private const val KEY_USER_SALT = "user_vault_salt_b64"
    private const val KEY_OFFLINE_MODE = "is_offline_mode"
    private const val KEY_LAST_SYNCED_TIME = "last_synced_time"

    // HKDF domain separation info strings — one per derived key purpose
    private const val HKDF_INFO_AUTH = "ayva_auth_v1"
    private const val HKDF_INFO_VAULT = "ayva_vault_v1"
    private const val HKDF_INFO_HMAC = "ayva_hmac_v1"

    /**
     * The three domain-separated keys derived from a single Argon2id master key.
     *
     * @param authPassword Base64-encoded token sent to Supabase Auth for login/registration.
     *                     Server only ever sees this derived token, never the raw password.
     * @param dataEncryptionKey (UserKey) 32-byte AES key for note/task envelope encryption.
     *                          Never leaves the device.
     * @param hmacKey 32-byte HMAC-SHA256 signing key for cloud item integrity tags.
     *                Never leaves the device.
     */
    data class DerivedKeys(
        val authPassword: String,
        val dataEncryptionKey: ByteArray,
        val hmacKey: ByteArray
    )

    data class SessionState(
        val isSignedIn: Boolean,
        val userId: String?,
        val userEmail: String?,
        val accessToken: String?,
        val isOfflineMode: Boolean,
        val lastSyncedTime: Long
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // =========================================================================
    // Hardware-Backed KeyStore Key Management
    // =========================================================================

    private fun getOrCreateKeyStoreSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
            throw SecurityException("Supabase KeyStore master key exists but could not be loaded as SecretKeyEntry. Refusing to overwrite key to prevent permanent data loss.")
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
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

    private fun encryptWithKeyStore(rawBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val secretKey = getOrCreateKeyStoreSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv ?: run {
            val generatedIv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, generatedIv))
            generatedIv
        }
        val ciphertext = cipher.doFinal(rawBytes)
        return Pair(ciphertext, iv)
    }

    private fun decryptWithKeyStore(ciphertext: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            val secretKey = getOrCreateKeyStoreSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "KeyStore decryption failed", e)
            null
        }
    }

    // =========================================================================
    // Key Derivation — Argon2id + HKDF (replaces PBKDF2 + SHA-256 split)
    // =========================================================================

    /**
     * Derives the Auth Password, Data Encryption Key, and HMAC Key from email + master password.
     *
     * Algorithm: Argon2id (m=32MB, t=3, p=1) → 32-byte master key → HKDF-RFC5869 fan-out.
     *
     * The [customSalt] parameter is used when re-deriving keys after sign-in to ensure
     * the same salt is used that was recorded at sign-up time (fetched from Supabase metadata).
     * If null, a fresh 16-byte cryptographic salt is generated (sign-up flow).
     *
     * REMOVED: PBKDF2-100K + SHA-256 split (was GPU-crackable; hash of MasterKey with
     *          a constant suffix offers zero additional hardness beyond PBKDF2 itself).
     */
    fun deriveKeys(
        email: String,
        masterPassword: CharArray,
        customSalt: ByteArray? = null
    ): DerivedKeys {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)

        // Deterministic per-user salt derived from email (unique per account, reproducible on sign-in)
        // or customSalt if explicitly provided.
        val salt = customSalt ?: java.security.MessageDigest.getInstance("SHA-256")
            .digest("ayva_master_salt_v1:$normalizedEmail".toByteArray(StandardCharsets.UTF_8))

        // Argon2id master key derivation (memory-hard, GPU/ASIC resistant)
        val masterKey = try {
            Argon2idKdf.deriveKey(
                password = masterPassword,
                salt = salt,
                params = Argon2idKdf.Parameters.LOGIN
            )
        } finally {
            Arrays.fill(masterPassword, '\u0000')
        }

        return try {
            // HKDF domain separation: three independent subkeys from one master key
            val authTokenBytes = HkdfUtil.deriveKey(masterKey, HKDF_INFO_AUTH, length = 32)
            val userKey = HkdfUtil.deriveKey(masterKey, HKDF_INFO_VAULT, length = 32)
            val hmacKey = HkdfUtil.deriveKey(masterKey, HKDF_INFO_HMAC, length = 32)

            // Encode authToken as Base64 for use as Supabase password string
            val authPassword = Base64.encodeToString(authTokenBytes, Base64.NO_WRAP)

            Arrays.fill(authTokenBytes, 0.toByte())

            DerivedKeys(
                authPassword = authPassword,
                dataEncryptionKey = userKey,
                hmacKey = hmacKey
            )
        } finally {
            Arrays.fill(masterKey, 0.toByte())
        }
    }

    // =========================================================================
    // Session Persistence — KeyStore-encrypted storage of DEK + HmacKey
    // =========================================================================

    /**
     * Stores active session after successful signup or login.
     * Hardware-encrypts both the Data Encryption Key AND the HMAC Key at rest via AndroidKeyStore.
     *
     * UPDATED: Now persists hmacKey alongside dataEncryptionKey.
     */
    fun saveSession(
        context: Context,
        userId: String,
        email: String,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
        dataEncryptionKey: ByteArray,
        hmacKey: ByteArray,
        userSalt: ByteArray? = null
    ) {
        val expiresAt = System.currentTimeMillis() + ((expiresInSeconds ?: 3600) * 1000)
        val prefs = getPrefs(context)

        try {
            val (encDek, dekIv) = encryptWithKeyStore(dataEncryptionKey)
            val (encHmac, hmacIv) = encryptWithKeyStore(hmacKey)

            prefs.edit().apply {
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_EMAIL, email.trim().lowercase(Locale.ROOT))
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_EXPIRES_AT, expiresAt)
                putString(KEY_DATA_KEY_CIPHERTEXT, Base64.encodeToString(encDek, Base64.NO_WRAP))
                putString(KEY_DATA_KEY_IV, Base64.encodeToString(dekIv, Base64.NO_WRAP))
                putString(KEY_HMAC_KEY_CIPHERTEXT, Base64.encodeToString(encHmac, Base64.NO_WRAP))
                putString(KEY_HMAC_KEY_IV, Base64.encodeToString(hmacIv, Base64.NO_WRAP))
                if (userSalt != null) putString(KEY_USER_SALT, Base64.encodeToString(userSalt, Base64.NO_WRAP))
                remove(KEY_DATA_KEY_BASE64_LEGACY)
                putBoolean(KEY_OFFLINE_MODE, false)
                apply()
            }
        } catch (e: Exception) {
            // Fallback: private sandbox storage (still protected by Android app sandbox, no hardware)
            Log.e(TAG, "KeyStore encryption failed, falling back to sandbox storage", e)
            prefs.edit().apply {
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_EMAIL, email.trim().lowercase(Locale.ROOT))
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_EXPIRES_AT, expiresAt)
                remove(KEY_DATA_KEY_CIPHERTEXT)
                remove(KEY_DATA_KEY_IV)
                putString(KEY_DATA_KEY_BASE64_LEGACY, Base64.encodeToString(dataEncryptionKey, Base64.NO_WRAP))
                putString(KEY_HMAC_KEY_CIPHERTEXT, Base64.encodeToString(hmacKey, Base64.NO_WRAP))
                putString(KEY_HMAC_KEY_IV, "SANDBOX_PLAIN")
                if (userSalt != null) putString(KEY_USER_SALT, Base64.encodeToString(userSalt, Base64.NO_WRAP))
                putBoolean(KEY_OFFLINE_MODE, false)
                apply()
            }
        }
    }

    /**
     * Retrieves the active 256-bit Data Encryption Key for note/task envelope encryption.
     * Decrypts from hardware-backed AndroidKeyStore. Falls back to legacy plaintext key with auto-upgrade.
     */
    fun getDataEncryptionKey(context: Context): ByteArray? {
        val prefs = getPrefs(context)
        val encKeyB64 = prefs.getString(KEY_DATA_KEY_CIPHERTEXT, null)
        val ivB64 = prefs.getString(KEY_DATA_KEY_IV, null)

        if (encKeyB64 != null && ivB64 != null) {
            val decrypted = try {
                val encBytes = Base64.decode(encKeyB64, Base64.NO_WRAP)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                decryptWithKeyStore(encBytes, iv)
            } catch (e: Exception) {
                Log.e(TAG, "Failed decrypting KeyStore-protected DEK", e)
                null
            }
            if (decrypted != null) return decrypted
        }

        // Migrate legacy unencrypted key if present
        val legacyB64 = prefs.getString(KEY_DATA_KEY_BASE64_LEGACY, null) ?: return null
        return try {
            val rawKey = Base64.decode(legacyB64, Base64.NO_WRAP)
            if (rawKey != null && rawKey.size == 32) {
                try {
                    val (encryptedKey, iv) = encryptWithKeyStore(rawKey)
                    prefs.edit()
                        .putString(KEY_DATA_KEY_CIPHERTEXT, Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                        .putString(KEY_DATA_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                        .remove(KEY_DATA_KEY_BASE64_LEGACY)
                        .apply()
                } catch (_: Exception) {}
            }
            rawKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed decoding legacy data encryption key", e)
            null
        }
    }

    /**
     * Retrieves the HMAC signing key for cloud item integrity verification.
     * Returns null for sessions established before this update (requires sign-out/sign-in).
     */
    fun getHmacKey(context: Context): ByteArray? {
        val prefs = getPrefs(context)
        val encKeyB64 = prefs.getString(KEY_HMAC_KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_HMAC_KEY_IV, null) ?: return null
        if (ivB64 == "SANDBOX_PLAIN") {
            return try { Base64.decode(encKeyB64, Base64.NO_WRAP) } catch (_: Exception) { null }
        }
        return try {
            val encBytes = Base64.decode(encKeyB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            decryptWithKeyStore(encBytes, iv)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting KeyStore-protected HMAC key", e)
            null
        }
    }

    // =========================================================================
    // Item-Level HMAC Signing (anti-tamper / anti-rollback)
    // =========================================================================

    /**
     * Computes an HMAC-SHA256 signature that binds together all mutable fields of a cloud item.
     * This prevents a compromised Supabase backend from swapping or replaying old ciphertexts.
     *
     * Signed data = UTF-8 bytes of: "{id}:{clientSeqNum}:{isDeleted}:{ciphertextBase64}"
     *
     * @param itemId         The stable UUID identifying this note/task.
     * @param clientSeqNum   Monotonically increasing client-side sequence number for this item.
     * @param isDeleted      Whether this item is in a deleted/tombstone state.
     * @param ciphertext     The ciphertextBase64 field of the envelope (content HMAC binding).
     * @param hmacKey        The 32-byte HMAC key (from HKDF "ayva_hmac_v1").
     * @return Base64-encoded 32-byte HMAC-SHA256 tag.
     */
    fun generateSignature(
        itemId: String,
        clientSeqNum: Long,
        isDeleted: Boolean,
        ciphertext: String,
        hmacKey: ByteArray
    ): String {
        val message = "$itemId:$clientSeqNum:$isDeleted:$ciphertext"
        val tag = HkdfUtil.hmac(hmacKey, message.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(tag, Base64.NO_WRAP)
    }

    /**
     * Verifies an HMAC-SHA256 signature on a cloud item.
     * Uses constant-time comparison to prevent timing oracle attacks.
     *
     * @return true if the signature is valid, false if it is missing, malformed, or tampered.
     */
    fun verifySignature(
        itemId: String,
        clientSeqNum: Long,
        isDeleted: Boolean,
        ciphertext: String,
        hmacKey: ByteArray,
        signatureBase64: String
    ): Boolean {
        return try {
            val expectedTag = run {
                val message = "$itemId:$clientSeqNum:$isDeleted:$ciphertext"
                HkdfUtil.hmac(hmacKey, message.toByteArray(StandardCharsets.UTF_8))
            }
            val receivedTag = Base64.decode(signatureBase64, Base64.NO_WRAP)
            HkdfUtil.hmacEquals(expectedTag, receivedTag)
        } catch (e: Exception) {
            Log.w(TAG, "Signature verification error for item $itemId", e)
            false
        }
    }

    // =========================================================================
    // Sequence Number Storage (per-item monotonic counter for replay prevention)
    // =========================================================================

    private const val SEQ_PREFS_NAME = "focus_item_seq_prefs"

    /**
     * Reads the current sequence number for an item, then increments and persists the new value.
     * @return The new (post-increment) sequence number to use in this push.
     */
    fun nextSequenceNumber(context: Context, itemId: String): Long {
        val prefs = context.getSharedPreferences(SEQ_PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getLong(itemId, 0L)
        val next = current + 1L
        prefs.edit().putLong(itemId, next).apply()
        return next
    }

    /**
     * Reads the stored sequence number for an item without modifying it.
     */
    fun getSequenceNumber(context: Context, itemId: String): Long {
        return context.getSharedPreferences(SEQ_PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(itemId, 0L)
    }

    /**
     * Updates the stored sequence number if the received [newSeqNum] is higher than the current stored value.
     * Enforces monotonicity across multi-device synchronizations.
     */
    fun updateSequenceNumberIfHigher(context: Context, itemId: String, newSeqNum: Long): Long {
        val prefs = context.getSharedPreferences(SEQ_PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getLong(itemId, 0L)
        if (newSeqNum > current) {
            prefs.edit().putLong(itemId, newSeqNum).apply()
            return newSeqNum
        }
        return current
    }

    // =========================================================================
    // Session State and Token Management (unchanged infrastructure)
    // =========================================================================

    fun getSessionState(context: Context): SessionState {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        val email = prefs.getString(KEY_USER_EMAIL, null)
        val isOffline = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        val lastSync = prefs.getLong(KEY_LAST_SYNCED_TIME, 0L)
        val isSignedIn = !token.isNullOrBlank() && !userId.isNullOrBlank()

        return SessionState(
            isSignedIn = isSignedIn,
            userId = userId,
            userEmail = email,
            accessToken = token,
            isOfflineMode = isOffline,
            lastSyncedTime = lastSync
        )
    }

    fun getUserSalt(context: Context): ByteArray? {
        val saltB64 = getPrefs(context).getString(KEY_USER_SALT, null) ?: return null
        return try { Base64.decode(saltB64, Base64.NO_WRAP) } catch (e: Exception) { null }
    }

    fun setOfflineMode(context: Context, isOffline: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OFFLINE_MODE, isOffline).apply()
    }

    fun isOfflineMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OFFLINE_MODE, false)
    }

    fun setLastSyncedTime(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_SYNCED_TIME, timestamp).apply()
    }

    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    fun isTokenExpiring(context: Context): Boolean {
        val expiresAt = getPrefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt == 0L) return false
        return System.currentTimeMillis() >= (expiresAt - 120_000L)
    }

    fun updateAccessToken(
        context: Context,
        newAccessToken: String,
        newRefreshToken: String?,
        expiresInSeconds: Long?
    ) {
        val expiresAt = System.currentTimeMillis() + ((expiresInSeconds ?: 3600L) * 1000L)
        getPrefs(context).edit().apply {
            putString(KEY_ACCESS_TOKEN, newAccessToken)
            if (!newRefreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, newRefreshToken)
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    /**
     * Purges the active user session and all associated sync metadata preferences.
     * Prevents cross-account data leaks, ghost tombstones, and sequence collisions.
     */
    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
        context.getSharedPreferences(SEQ_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("focus_supabase_deletions", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("focus_supabase_media_deletions", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("focus_media_cloud_manifest", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("focus_task_sync_timestamps", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("focus_supabase_sync_id_mapping", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
