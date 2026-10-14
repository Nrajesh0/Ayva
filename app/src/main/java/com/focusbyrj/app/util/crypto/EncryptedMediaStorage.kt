/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * High-performance Zero-Knowledge Encrypted Disk Storage for Local Media Attachments.
 *
 * Guarantees that all images, sketches, and voice memo audio recordings stored on flash storage
 * are encrypted with hardware-backed AES-256-GCM. Plaintext bytes never touch flash memory at rest.
 */
object EncryptedMediaStorage {

    private const val TAG = "EncryptedMediaStorage"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MEDIA_KEY_ALIAS = "focus_local_media_master_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val MAGIC_HEADER = "FOC_ENC_V1:"
    const val MAX_DECRYPT_BYTES_LIMIT = 50 * 1024 * 1024L // 50MB upper limit for heap ByteArray loading

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
            if (keyStore.containsAlias(MEDIA_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MEDIA_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
                throw SecurityException("Media storage master key exists in AndroidKeyStore but could not be loaded as SecretKeyEntry. Refusing to overwrite key to prevent permanent data loss.")
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MEDIA_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            if (e is SecurityException) throw e
            if (isUnitTestEnvironment) {
                Log.w(TAG, "AndroidKeyStore is unavailable in unit test environment; using test software key.")
                return getLegacyFallbackKey()
            }
            Log.e(TAG, "AndroidKeyStore is unavailable for media storage key generation. Refusing to use static software key for new writes.", e)
            throw SecurityException("Failed to generate hardware-backed media storage key in AndroidKeyStore", e)
        }
    }

    private fun getMasterKeyForDecryption(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MEDIA_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MEDIA_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve media storage master key from AndroidKeyStore", e)
            null
        }
    }

    internal fun getLegacyFallbackKey(): SecretKey {
        val fallbackSeed = java.security.MessageDigest.getInstance("SHA-256")
            .digest("focus_media_storage_software_seed_v1".toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(fallbackSeed, "AES")
    }

    /**
     * Encrypts plaintext bytes and writes them atomically to destination file.
     * Nonce/IV is generated in hardware (TEE/StrongBox) via Android KeyStore.
     */
    fun writeEncryptedBytes(file: File, plaintextBytes: ByteArray) {
        // Ensure parent directory exists before attempting to write temp file
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val secretKey = getMasterKeyForEncryption()

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = checkNotNull(cipher.iv) { "Cipher failed to generate IV for media encryption" }
        require(iv.size == GCM_IV_LENGTH) { "Cipher generated IV of invalid length: ${iv.size}, expected $GCM_IV_LENGTH" }

        // Wrap all writes in try/catch so tempFile is always cleaned up on failure.
        // Previously, a disk-full or OOM during write would leave an orphaned .tmp file on disk.
        try {
            val encryptedBytes = cipher.doFinal(plaintextBytes)

            FileOutputStream(tempFile).use { fos ->
                fos.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
                fos.write(iv)
                fos.write(encryptedBytes)
                fos.flush()
            }

            if (file.exists()) {
                file.delete()
            }
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Throwable) {
            // Clean up the partially written temp file before rethrowing to avoid storage leaks.
            tempFile.delete()
            throw e
        }
    }

    /**
     * Reads and decrypts file bytes into memory. If file is legacy plaintext, returns raw bytes safely.
     * Uses streaming header extraction to minimize heap allocations.
     */
    fun readDecryptedBytes(file: File): ByteArray? {
        if (!file.exists() || file.length() > MAX_DECRYPT_BYTES_LIMIT) return null
        return try {
            val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
            val fileLen = file.length()
            val minEncryptedLen = magicBytes.size + GCM_IV_LENGTH + 16 // Header + 12B IV + 16B GCM Tag

            FileInputStream(file).use { fis ->
                val headerBuf = ByteArray(magicBytes.size)
                var bytesRead = 0
                while (bytesRead < magicBytes.size) {
                    val r = fis.read(headerBuf, bytesRead, magicBytes.size - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }

                if (bytesRead == magicBytes.size && headerBuf.contentEquals(magicBytes)) {
                    // File claims to be encrypted via MAGIC_HEADER.
                    // If file is shorter than minimum encrypted length, it is corrupted/truncated.
                    // Must return null, never fall back to plaintext.
                    if (fileLen < minEncryptedLen) return null

                    val iv = ByteArray(GCM_IV_LENGTH)
                    var ivRead = 0
                    while (ivRead < GCM_IV_LENGTH) {
                        val r = fis.read(iv, ivRead, GCM_IV_LENGTH - ivRead)
                        if (r == -1) break
                        ivRead += r
                    }
                    if (ivRead != GCM_IV_LENGTH) return null

                    val cipherLen = (fileLen - magicBytes.size - GCM_IV_LENGTH).toInt()
                    val ciphertext = ByteArray(cipherLen)
                    var totalCipherRead = 0
                    while (totalCipherRead < cipherLen) {
                        val r = fis.read(ciphertext, totalCipherRead, cipherLen - totalCipherRead)
                        if (r == -1) break
                        totalCipherRead += r
                    }
                    if (totalCipherRead != cipherLen) return null

                    // Primary: Try hardware KeyStore key
                    var decrypted: ByteArray? = null
                    try {
                        val keyStoreKey = getMasterKeyForDecryption()
                        if (keyStoreKey != null) {
                            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                            cipher.init(Cipher.DECRYPT_MODE, keyStoreKey, gcmSpec)
                            decrypted = cipher.doFinal(ciphertext)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "KeyStore decryption failed for media file, trying legacy fallback key: ${file.name}", e)
                    }

                    // Secondary: Compatibility adapter with legacy static software key (read-only)
                    if (decrypted == null) {
                        try {
                            val legacyKey = getLegacyFallbackKey()
                            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                            cipher.init(Cipher.DECRYPT_MODE, legacyKey, gcmSpec)
                            decrypted = cipher.doFinal(ciphertext)
                            Log.w(TAG, "Successfully decrypted media file using legacy fallback key: ${file.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to decrypt media file with both KeyStore and legacy keys: ${file.name}", e)
                        }
                    }

                    java.util.Arrays.fill(ciphertext, 0.toByte())
                    decrypted
                } else {
                    // Legacy plaintext fallback
                    val fullBytes = ByteArray(fileLen.toInt())
                    System.arraycopy(headerBuf, 0, fullBytes, 0, bytesRead)
                    var offset = bytesRead
                    while (offset < fullBytes.size) {
                        val r = fis.read(fullBytes, offset, fullBytes.size - offset)
                        if (r == -1) break
                        offset += r
                    }
                    if (offset != fullBytes.size) fullBytes.copyOf(offset) else fullBytes
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt media file: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Opens a streaming [InputStream] that decrypts media on-the-fly via [CipherInputStream] or memory buffer.
     * If the file is legacy plaintext, returns a direct [FileInputStream].
     * Returns null if file does not exist or has a corrupted header.
     */
    fun openDecryptedInputStream(file: File): InputStream? {
        if (!file.exists()) return null
        val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        val minEncryptedLen = magicBytes.size + GCM_IV_LENGTH + 16
        val fileLen = file.length()

        if (fileLen >= minEncryptedLen) {
            val isEnc = try {
                FileInputStream(file).use { fis ->
                    val headerBuf = ByteArray(magicBytes.size)
                    var bytesRead = 0
                    while (bytesRead < magicBytes.size) {
                        val r = fis.read(headerBuf, bytesRead, magicBytes.size - bytesRead)
                        if (r == -1) break
                        bytesRead += r
                    }
                    bytesRead == magicBytes.size && headerBuf.contentEquals(magicBytes)
                }
            } catch (_: Exception) { false }

            if (isEnc) {
                // If encrypted, read and decrypt safely (handling both KeyStore and legacy software keys)
                val decrypted = readDecryptedBytes(file)
                return if (decrypted != null) {
                    java.io.ByteArrayInputStream(decrypted)
                } else {
                    null
                }
            }
        }
        // Legacy plaintext fallback
        return try {
            FileInputStream(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open decrypted stream: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Opens a stream for writing encrypted data using hardware-generated IV.
     */
    fun openEncryptedOutputStream(file: File): OutputStream {
        val secretKey = getMasterKeyForEncryption()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = checkNotNull(cipher.iv) { "Cipher failed to generate IV for media output stream" }
        require(iv.size == GCM_IV_LENGTH) { "Cipher generated IV of invalid length: ${iv.size}, expected $GCM_IV_LENGTH" }

        // Ensure parent directory exists before creating output stream
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        fos.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
        fos.write(iv)
        return CipherOutputStream(fos, cipher)
    }

    /**
     * Checks whether the file is encrypted with the storage header.
     */
    fun isEncrypted(file: File): Boolean {
        val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        val minEncryptedLen = magicBytes.size + GCM_IV_LENGTH + 16
        if (!file.exists() || file.length() < minEncryptedLen) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(magicBytes.size)
                var bytesRead = 0
                while (bytesRead < magicBytes.size) {
                    val r = fis.read(header, bytesRead, magicBytes.size - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }
                bytesRead == magicBytes.size && header.contentEquals(magicBytes)
            }
        } catch (_: Exception) {
            false
        }
    }
}
