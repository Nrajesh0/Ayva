/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed AES-256-GCM encryption engine for internal data safety snapshots (F05).
 *
 * Protects daily, pre-operation, and anomaly snapshots stored at rest in internal storage.
 * Eliminates plaintext JSON exposure while maintaining fast, atomic snapshot creation and recovery.
 *
 * Wire format (v1):
 *   [Magic Header: 4 bytes] 'A' 'Y' 'S' 'N' (AYVA Snapshot)
 *   [Version: 1 byte]       0x01
 *   [IV / Nonce: 12 bytes]  Random cryptographic nonce
 *   [Ciphertext + Tag]      AES-256-GCM encrypted payload with 128-bit authentication tag
 *                           Header metadata (Magic + Version + IV) is authenticated via GCM AAD.
 */
object SnapshotCryptoEngine {

    private const val TAG = "SnapshotCryptoEngine"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "focus_snapshot_master_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val HEADER_VERSION_1: Byte = 0x01

    private val MAGIC_HEADER = byteArrayOf(
        'A'.code.toByte(),
        'Y'.code.toByte(),
        'S'.code.toByte(),
        'N'.code.toByte()
    )

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
                throw SecurityException("Snapshot master key exists in AndroidKeyStore but could not be loaded as SecretKeyEntry. Refusing to overwrite key to prevent permanent data loss.")
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
            Log.e(TAG, "AndroidKeyStore is unavailable for snapshot master key generation. Refusing to use static software key for new writes.", e)
            throw SecurityException("Failed to generate hardware-backed snapshot master key in AndroidKeyStore", e)
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
            Log.w(TAG, "Failed to retrieve snapshot master key from AndroidKeyStore", e)
            null
        }
    }

    internal fun getLegacyFallbackKey(): SecretKey {
        val fallbackSeed = java.security.MessageDigest.getInstance("SHA-256")
            .digest("focus_snapshot_software_seed_v1".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(fallbackSeed, "AES")
    }

    /**
     * Checks if the given byte array represents an encrypted snapshot container.
     */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC_HEADER.size + 1 + IV_LENGTH + 16) return false
        for (i in MAGIC_HEADER.indices) {
            if (data[i] != MAGIC_HEADER[i]) return false
        }
        return data[MAGIC_HEADER.size] == HEADER_VERSION_1
    }

    /**
     * Checks if a snapshot file on disk begins with the snapshot magic header.
     */
    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_HEADER.size + 1 + IV_LENGTH + 16) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(MAGIC_HEADER.size + 1)
                val read = fis.read(header)
                if (read < header.size) return false
                for (i in MAGIC_HEADER.indices) {
                    if (header[i] != MAGIC_HEADER[i]) return false
                }
                header[MAGIC_HEADER.size] == HEADER_VERSION_1
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext snapshot payload into an authenticated container.
     */
    fun encryptSnapshot(plaintext: ByteArray): ByteArray {
        val masterKey = getMasterKeyForEncryption()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = checkNotNull(cipher.iv) { "Cipher failed to generate IV for snapshot encryption" }
        require(iv.size == IV_LENGTH) { "Cipher generated IV of invalid length: ${iv.size}, expected $IV_LENGTH" }
        val aad = MAGIC_HEADER + byteArrayOf(HEADER_VERSION_1) + iv
        cipher.updateAAD(aad)
        val ciphertextWithTag = cipher.doFinal(plaintext)

        val result = ByteArray(MAGIC_HEADER.size + 1 + IV_LENGTH + ciphertextWithTag.size)
        var offset = 0
        System.arraycopy(MAGIC_HEADER, 0, result, offset, MAGIC_HEADER.size)
        offset += MAGIC_HEADER.size
        result[offset] = HEADER_VERSION_1
        offset += 1
        System.arraycopy(iv, 0, result, offset, IV_LENGTH)
        offset += IV_LENGTH
        System.arraycopy(ciphertextWithTag, 0, result, offset, ciphertextWithTag.size)

        return result
    }

    /**
     * Decrypts an authenticated snapshot container.
     */
    fun decryptSnapshot(encryptedData: ByteArray): ByteArray {
        if (!isEncrypted(encryptedData)) {
            throw IllegalArgumentException("Data is not an encrypted snapshot container (invalid magic header or version).")
        }

        var offset = MAGIC_HEADER.size
        val version = encryptedData[offset]
        offset += 1
        if (version != HEADER_VERSION_1) {
            throw IllegalArgumentException("Unsupported snapshot container version: $version")
        }

        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(encryptedData, offset, iv, 0, IV_LENGTH)
        offset += IV_LENGTH

        val ciphertextWithTagLength = encryptedData.size - offset
        val ciphertextWithTag = ByteArray(ciphertextWithTagLength)
        System.arraycopy(encryptedData, offset, ciphertextWithTag, 0, ciphertextWithTagLength)

        val aad = MAGIC_HEADER + byteArrayOf(version) + iv

        // Decrypt using KeyStore hardware key, or unit test fallback
        val masterKey = getMasterKeyForDecryption() ?: if (isUnitTestEnvironment) {
            getLegacyFallbackKey()
        } else {
            throw SecurityException("Snapshot master key could not be loaded from AndroidKeyStore.")
        }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }
}
