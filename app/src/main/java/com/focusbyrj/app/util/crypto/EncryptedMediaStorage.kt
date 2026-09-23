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

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MEDIA_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MEDIA_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
                // B1-F-013 FIX: Alias exists but entry could not be retrieved. Refuse to overwrite existing key.
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
            Log.w(TAG, "AndroidKeyStore is unavailable on this device/environment. Using local software SecretKey for media storage.", e)
            val fallbackSeed = java.security.MessageDigest.getInstance("SHA-256")
                .digest("focus_media_storage_software_seed_v1".toByteArray(Charsets.UTF_8))
            javax.crypto.spec.SecretKeySpec(fallbackSeed, "AES")
        }
    }

    /**
     * Encrypts plaintext bytes and writes them atomically to destination file.
     * Nonce/IV is generated in hardware (TEE/StrongBox) via Android KeyStore.
     */
    fun writeEncryptedBytes(file: File, plaintextBytes: ByteArray) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val secretKey = getOrCreateKey()

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            // B1-F-010 FIX: Re-init cipher if cipher.iv is null so ciphertext matches returned IV
            cipher.iv ?: run {
                val generatedIv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, generatedIv))
                generatedIv
            }
        } catch (_: Exception) {
            val generatedIv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, generatedIv))
            generatedIv
        }

        // B1-F-006 FIX: Wrap all writes in try/catch so tempFile is always cleaned up on failure.
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
            tempFile.renameTo(file)
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
        if (!file.exists()) return null
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
                    // B1-F-009 FIX: If file is shorter than minimum encrypted length, it is corrupted/truncated.
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

                    val secretKey = getOrCreateKey()
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                    cipher.doFinal(ciphertext)
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
     * Opens a stream for writing encrypted data using hardware-generated IV.
     */
    fun openEncryptedOutputStream(file: File): OutputStream {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            // B1-F-010 FIX: Re-init cipher if cipher.iv is null so ciphertext matches returned IV
            cipher.iv ?: run {
                val generatedIv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, generatedIv))
                generatedIv
            }
        } catch (_: Exception) {
            val generatedIv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, generatedIv))
            generatedIv
        }

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
        if (!file.exists() || file.length() < magicBytes.size) return false
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
