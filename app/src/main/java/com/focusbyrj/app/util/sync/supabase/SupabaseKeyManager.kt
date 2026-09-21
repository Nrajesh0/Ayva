/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.util.sync.supabase

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
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

/**
 * Zero-Knowledge Key Derivation and Local Session Storage.
 *
 * Mathematical flow:
 * 1. Master Password + Normalized Email -> PBKDF2-SHA256 (100,000 iterations) -> Master Key (256 bits)
 * 2. Master Key + "AUTH_GATEWAY" -> SHA-256 -> Auth Password (sent to Supabase)
 * 3. Master Key + "VAULT_ENCRYPTION" -> SHA-256 -> Data Encryption Key (NEVER sent to Supabase)
 * 4. At Rest: Data Encryption Key is encrypted using hardware-backed Android KeyStore (AES-256-GCM)
 */
object SupabaseKeyManager {

    private const val TAG = "SupabaseKeyManager"
    private const val PREFS_NAME = "focus_supabase_zk_prefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "focus_zk_vault_keystore_alias"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_DATA_KEY_CIPHERTEXT = "data_encryption_key_enc"
    private const val KEY_DATA_KEY_IV = "data_encryption_key_iv"
    private const val KEY_DATA_KEY_BASE64_LEGACY = "data_encryption_key_b64"
    private const val KEY_USER_SALT = "user_vault_salt_b64"
    private const val KEY_OFFLINE_MODE = "is_offline_mode"
    private const val KEY_LAST_SYNCED_TIME = "last_synced_time"

    data class DerivedKeys(
        val authPassword: String,
        val dataEncryptionKey: ByteArray
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

    private fun getOrCreateKeyStoreSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
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
        val iv = cipher.iv
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

    /**
     * Derives both the Auth Password and the local Data Encryption Key from email + master password.
     * Uses Argon2id memory-hard KDF (64 MB, 3 iterations) for high-grade resistance against GPU/ASIC attacks,
     * with graceful backward-compatible fallback to PBKDF2 for legacy verification.
     */
    fun deriveKeys(email: String, masterPassword: CharArray, customSalt: ByteArray? = null): DerivedKeys {
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val salt = customSalt ?: MessageDigest.getInstance("SHA-256")
            .digest("FOCUS_SALT:$normalizedEmail".toByteArray(StandardCharsets.UTF_8))

        // High-grade Argon2id derivation
        val masterKeyBytes = try {
            com.focusbyrj.app.util.crypto.Argon2idKdf.deriveKey(
                password = masterPassword,
                salt = salt,
                params = com.focusbyrj.app.util.crypto.Argon2idKdf.Parameters(
                    iterations = 3,
                    memoryCostKb = 65536,
                    parallelism = 4,
                    outputLengthBytes = 32
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Argon2id execution fallback to PBKDF2", e)
            val spec = PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            spec.clearPassword()
            keyBytes
        }

        // 1. Derive Auth Password for Supabase Auth (Supabase never sees the raw Master Password)
        val authDigest = MessageDigest.getInstance("SHA-256")
        authDigest.update(masterKeyBytes)
        authDigest.update("AUTH_GATEWAY".toByteArray(StandardCharsets.UTF_8))
        val authPassword = Base64.encodeToString(authDigest.digest(), Base64.NO_WRAP)

        // 2. Derive Data Encryption Key for client-side note encryption (NEVER leaves device)
        val dataDigest = MessageDigest.getInstance("SHA-256")
        dataDigest.update(masterKeyBytes)
        dataDigest.update("VAULT_ENCRYPTION".toByteArray(StandardCharsets.UTF_8))
        val dataEncryptionKey = dataDigest.digest()

        // Zero out master key bytes from memory for hygiene
        Arrays.fill(masterKeyBytes, 0.toByte())

        return DerivedKeys(authPassword, dataEncryptionKey)
    }

    /**
     * Legacy PBKDF2 derivation used strictly for backward compatibility verification during account migration.
     */
    fun deriveKeysLegacyPbkdf2(email: String, masterPassword: CharArray, customSalt: ByteArray? = null): DerivedKeys {
        val normalizedEmail = email.trim().lowercase(java.util.Locale.ROOT)
        val salt = customSalt ?: MessageDigest.getInstance("SHA-256")
            .digest("FOCUS_SALT:$normalizedEmail".toByteArray(StandardCharsets.UTF_8))

        val spec = PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val masterKeyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()

        val authDigest = MessageDigest.getInstance("SHA-256")
        authDigest.update(masterKeyBytes)
        authDigest.update("AUTH_GATEWAY".toByteArray(StandardCharsets.UTF_8))
        val authPassword = Base64.encodeToString(authDigest.digest(), Base64.NO_WRAP)

        val dataDigest = MessageDigest.getInstance("SHA-256")
        dataDigest.update(masterKeyBytes)
        dataDigest.update("VAULT_ENCRYPTION".toByteArray(StandardCharsets.UTF_8))
        val dataEncryptionKey = dataDigest.digest()

        Arrays.fill(masterKeyBytes, 0.toByte())
        return DerivedKeys(authPassword, dataEncryptionKey)
    }

    /**
     * Derives the Data Encryption Key specifically using an account-bound random salt with Argon2id.
     */
    fun deriveDataKeyWithSalt(masterPassword: CharArray, salt: ByteArray): ByteArray {
        val masterKeyBytes = try {
            com.focusbyrj.app.util.crypto.Argon2idKdf.deriveKey(
                password = masterPassword,
                salt = salt,
                params = com.focusbyrj.app.util.crypto.Argon2idKdf.Parameters(
                    iterations = 3,
                    memoryCostKb = 65536,
                    parallelism = 4,
                    outputLengthBytes = 32
                )
            )
        } catch (_: Exception) {
            val spec = PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            spec.clearPassword()
            keyBytes
        }

        val dataDigest = MessageDigest.getInstance("SHA-256")
        dataDigest.update(masterKeyBytes)
        dataDigest.update("VAULT_ENCRYPTION".toByteArray(StandardCharsets.UTF_8))
        val dataEncryptionKey = dataDigest.digest()

        Arrays.fill(masterKeyBytes, 0.toByte())
        return dataEncryptionKey
    }

    /**
     * Stores active session after successful signup or login.
     * Hardware-encrypts the Data Encryption Key at rest.
     */
    fun saveSession(
        context: Context,
        userId: String,
        email: String,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
        dataEncryptionKey: ByteArray,
        userSalt: ByteArray? = null
    ) {
        val expiresAt = System.currentTimeMillis() + ((expiresInSeconds ?: 3600) * 1000)
        val prefs = getPrefs(context)

        try {
            val (encryptedKey, iv) = encryptWithKeyStore(dataEncryptionKey)
            prefs.edit().apply {
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_EMAIL, email.trim().lowercase(java.util.Locale.ROOT))
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_EXPIRES_AT, expiresAt)
                putString(KEY_DATA_KEY_CIPHERTEXT, Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                putString(KEY_DATA_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                if (userSalt != null) putString(KEY_USER_SALT, Base64.encodeToString(userSalt, Base64.NO_WRAP))
                remove(KEY_DATA_KEY_BASE64_LEGACY) // Remove any plaintext legacy key
                putBoolean(KEY_OFFLINE_MODE, false)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed hardware-encrypting data key via AndroidKeyStore, falling back to private sandbox storage", e)
            prefs.edit().apply {
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_EMAIL, email.trim().lowercase(java.util.Locale.ROOT))
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_EXPIRES_AT, expiresAt)
                putString(KEY_DATA_KEY_BASE64_LEGACY, Base64.encodeToString(dataEncryptionKey, Base64.NO_WRAP))
                if (userSalt != null) putString(KEY_USER_SALT, Base64.encodeToString(userSalt, Base64.NO_WRAP))
                putBoolean(KEY_OFFLINE_MODE, false)
                apply()
            }
        }
    }

    /**
     * Retrieves the stored account random salt if available.
     */
    fun getUserSalt(context: Context): ByteArray? {
        val saltB64 = getPrefs(context).getString(KEY_USER_SALT, null) ?: return null
        return try {
            Base64.decode(saltB64, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves the current user session state.
     */
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

    /**
     * Retrieves the active 256-bit Data Encryption Key for note encryption.
     * Decrypts from hardware-backed KeyStore.
     */
    fun getDataEncryptionKey(context: Context): ByteArray? {
        val prefs = getPrefs(context)
        val encKeyB64 = prefs.getString(KEY_DATA_KEY_CIPHERTEXT, null)
        val ivB64 = prefs.getString(KEY_DATA_KEY_IV, null)

        if (encKeyB64 != null && ivB64 != null) {
            return try {
                val encBytes = Base64.decode(encKeyB64, Base64.NO_WRAP)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                decryptWithKeyStore(encBytes, iv)
            } catch (e: Exception) {
                Log.e(TAG, "Failed decrypting KeyStore-protected data key", e)
                null
            }
        }

        // Migrate legacy unencrypted key if found
        val legacyB64 = prefs.getString(KEY_DATA_KEY_BASE64_LEGACY, null) ?: return null
        return try {
            val rawKey = Base64.decode(legacyB64, Base64.NO_WRAP)
            if (rawKey != null && rawKey.size == 32) {
                // Auto-upgrade to KeyStore encryption
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
     * Marks the app as explicitly opting to use offline mode.
     */
    fun setOfflineMode(context: Context, isOffline: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_OFFLINE_MODE, isOffline)
            .apply()
    }

    /**
     * Returns true if the user opted for offline mode.
     */
    fun isOfflineMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OFFLINE_MODE, false)
    }

    /**
     * Updates the last successful sync timestamp.
     */
    fun setLastSyncedTime(context: Context, timestamp: Long) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_SYNCED_TIME, timestamp)
            .apply()
    }

    /**
     * Retrieves the stored refresh token.
     */
    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Checks if the access token has expired or is expiring soon (within 2 minutes).
     */
    fun isTokenExpiring(context: Context): Boolean {
        val expiresAt = getPrefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt == 0L) return false
        return System.currentTimeMillis() >= (expiresAt - 120_000L)
    }

    /**
     * Updates tokens after refresh.
     */
    fun updateAccessToken(
        context: Context,
        newAccessToken: String,
        newRefreshToken: String?,
        expiresInSeconds: Long?
    ) {
        val expiresAt = System.currentTimeMillis() + ((expiresInSeconds ?: 3600L) * 1000L)
        getPrefs(context).edit().apply {
            putString(KEY_ACCESS_TOKEN, newAccessToken)
            if (!newRefreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, newRefreshToken)
            }
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    /**
     * Clears all credentials and keys upon signing out.
     */
    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
