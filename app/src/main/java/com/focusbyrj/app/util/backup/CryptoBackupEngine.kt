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

package com.focusbyrj.app.util.backup

import com.focusbyrj.app.util.crypto.Argon2idKdf
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption engine for backup files.
 *
 * PATTERN 1 UPDATE: Replaced PBKDF2-100K KDF with Argon2id (m=32MB, t=3, p=1).
 *
 * Format v2 (new backups):
 *   [Magic: 4 bytes]  "FBCK"
 *   [Version: 1 byte] 0x02
 *   [Argon2id Salt: 16 bytes]
 *   [GCM IV / Nonce: 12 bytes]
 *   [AES-256-GCM Ciphertext + 128-bit Authentication Tag]
 *
 * Format v1 (legacy, read-only backward compat):
 *   [Magic: 4 bytes]  "FBCK"
 *   [Version: 1 byte] 0x01
 *   [PBKDF2 Salt: 16 bytes]
 *   [GCM IV / Nonce: 12 bytes]
 *   [AES-256-GCM Ciphertext + 128-bit Authentication Tag]
 *
 * REMOVED:
 *   - PBKDF2-100K key derivation in encrypt()
 *   - FORMAT_VERSION 0x01 used for new backups
 *
 * ADDED:
 *   - Argon2id (real native library via argon2kt:1.4.0) for new backup encryption
 *   - Format version 0x02 written for all new backups
 *   - Format version 0x01 backward-compatible decryption path (for restoring old backups)
 */
object CryptoBackupEngine {

    private val MAGIC_HEADER = byteArrayOf('F'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte())

    private const val FORMAT_VERSION_V1: Byte = 0x01  // PBKDF2-100K (legacy, read-only)
    private const val FORMAT_VERSION_V2: Byte = 0x02  // Argon2id (current)

    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    // Legacy PBKDF2 constants (kept for v1 backward-compat decryption only)
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

    private fun readFully(inputStream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = inputStream.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) return false
            offset += bytesRead
        }
        return true
    }

    /**
     * Opens a streaming CipherOutputStream for encrypting data on-the-fly.
     * Writes the v2 format header (Magic + Version 0x02 + Salt + IV) directly to [outputStream].
     * 
     * Streaming ensures large archives (including photos, sketches, and audio memos)
     * are encrypted with zero heap memory buffering, completely preventing OutOfMemoryError crashes.
     */
    fun openEncryptingStream(outputStream: OutputStream, passwordChars: CharArray): OutputStream {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        var derivedKeyBytes: ByteArray? = null
        try {
            derivedKeyBytes = Argon2idKdf.deriveKey(
                password = passwordChars,
                salt = salt,
                params = Argon2idKdf.Parameters.LOGIN  // m=32MB, t=3, p=1
            )

            val secretKey = SecretKeySpec(derivedKeyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // Write v2 header
            outputStream.write(MAGIC_HEADER)
            outputStream.write(byteArrayOf(FORMAT_VERSION_V2))
            outputStream.write(salt)
            outputStream.write(iv)
            outputStream.flush()

            return javax.crypto.CipherOutputStream(outputStream, cipher)
        } finally {
            derivedKeyBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    /**
     * Opens a streaming CipherInputStream for decrypting an encrypted backup archive on-the-fly.
     * Verifies format headers and supports both:
     *   v2 (0x02): Argon2id key derivation (new, current)
     *   v1 (0x01): PBKDF2-100K key derivation (legacy, backward-compat restore)
     * 
     * Throws [IllegalArgumentException] for invalid/corrupted headers.
     * Throws [SecurityException] for authentication failures.
     */
    fun openDecryptingStream(inputStream: InputStream, passwordChars: CharArray): InputStream {
        var derivedKeyBytes: ByteArray? = null
        try {
            val header = ByteArray(4)
            if (!readFully(inputStream, header) || !header.contentEquals(MAGIC_HEADER)) {
                throw IllegalArgumentException("Not a valid Focus Backup archive file.")
            }

            val versionByte = inputStream.read()
            if (versionByte == -1) {
                throw IllegalArgumentException("Corrupted backup header: unexpected end of stream.")
            }
            val isV2 = versionByte == FORMAT_VERSION_V2.toInt()
            val isV1 = versionByte == FORMAT_VERSION_V1.toInt()

            if (!isV1 && !isV2) {
                throw IllegalArgumentException("Unsupported backup format version: $versionByte. Expected 1 (legacy) or 2 (current).")
            }

            val salt = ByteArray(SALT_LENGTH)
            if (!readFully(inputStream, salt)) {
                throw IllegalArgumentException("Corrupted backup header: incomplete salt.")
            }

            val iv = ByteArray(IV_LENGTH)
            if (!readFully(inputStream, iv)) {
                throw IllegalArgumentException("Corrupted backup header: incomplete IV.")
            }

            derivedKeyBytes = if (isV2) {
                // v2: Argon2id key derivation (Pattern 1)
                Argon2idKdf.deriveKey(
                    password = passwordChars,
                    salt = salt,
                    params = Argon2idKdf.Parameters.LOGIN
                )
            } else {
                // v1: Legacy PBKDF2 (backward compat for old backups)
                val keySpec = javax.crypto.spec.PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
                try {
                    val keyFactory = javax.crypto.SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                    keyFactory.generateSecret(keySpec).encoded
                } finally {
                    keySpec.clearPassword()
                }
            }

            val secretKey = SecretKeySpec(derivedKeyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            return javax.crypto.CipherInputStream(inputStream, cipher)
        } catch (e: Exception) {
            if (e is IllegalArgumentException || e is SecurityException) throw e
            throw SecurityException("Incorrect password or corrupted backup file.", e)
        } finally {
            derivedKeyBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    /**
     * Encrypts plaintext bytes using Argon2id + AES-256-GCM and writes to [outputStream].
     * Writes a v2 format header. Uses streaming cipher internally.
     */
    fun encrypt(plaintext: ByteArray, passwordChars: CharArray, outputStream: OutputStream) {
        val passwordCopy = passwordChars.clone()
        try {
            openEncryptingStream(outputStream, passwordCopy).use { cipherOut ->
                cipherOut.write(plaintext)
                cipherOut.flush()
            }
        } finally {
            Arrays.fill(passwordCopy, '\u0000')
            Arrays.fill(passwordChars, '\u0000')
        }
    }

    /**
     * Reads and decrypts an encrypted backup stream into memory.
     * Preserved for backward compatibility with callers expecting in-memory ByteArrays.
     *
     * Throws [IllegalArgumentException] for invalid/corrupted files.
     * Throws [SecurityException] for incorrect password or tampered ciphertext.
     */
    fun decrypt(inputStream: InputStream, passwordChars: CharArray): ByteArray {
        val passwordCopy = passwordChars.clone()
        try {
            return openDecryptingStream(inputStream, passwordCopy).use { cipherIn ->
                cipherIn.readBytes()
            }
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            if (e is SecurityException) throw e
            throw SecurityException("Incorrect password or corrupted backup file.", e)
        } finally {
            Arrays.fill(passwordCopy, '\u0000')
            Arrays.fill(passwordChars, '\u0000')
        }
    }
}
