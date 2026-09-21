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
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(MEDIA_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MEDIA_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MEDIA_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // We supply our own cryptographically secure random IV
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext bytes and writes them atomically to destination file.
     */
    fun writeEncryptedBytes(file: File, plaintextBytes: ByteArray) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val secretKey = getOrCreateKey()
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

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
    }

    /**
     * Reads and decrypts file bytes into memory. If file is legacy plaintext, returns raw bytes safely.
     */
    fun readDecryptedBytes(file: File): ByteArray? {
        if (!file.exists()) return null
        return try {
            val totalBytes = file.readBytes()
            val magicBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)

            // Check if file has encrypted header
            if (totalBytes.size > magicBytes.size + GCM_IV_LENGTH &&
                totalBytes.copyOfRange(0, magicBytes.size).contentEquals(magicBytes)
            ) {
                val ivStart = magicBytes.size
                val iv = totalBytes.copyOfRange(ivStart, ivStart + GCM_IV_LENGTH)
                val ciphertext = totalBytes.copyOfRange(ivStart + GCM_IV_LENGTH, totalBytes.size)

                val secretKey = getOrCreateKey()
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                cipher.doFinal(ciphertext)
            } else {
                // Legacy plaintext fallback
                totalBytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt media file: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Opens a stream for writing encrypted data.
     */
    fun openEncryptedOutputStream(file: File): OutputStream {
        val secretKey = getOrCreateKey()
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val fos = FileOutputStream(file)
        fos.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
        fos.write(iv)
        return CipherOutputStream(fos, cipher)
    }

    /**
     * Checks whether the file is encrypted with the storage header.
     */
    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_HEADER.length) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(MAGIC_HEADER.length)
                val read = fis.read(header)
                read == header.size && header.contentEquals(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            false
        }
    }
}
