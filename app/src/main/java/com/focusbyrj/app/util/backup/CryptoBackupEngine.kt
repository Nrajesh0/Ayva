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

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Military-grade AES-256-GCM encryption engine with PBKDF2-HMAC-SHA256 key derivation.
 *
 * File Structure:
 * [Magic Header: 4 bytes] ("FBCK")
 * [Format Version: 1 byte] (0x01)
 * [PBKDF2 Salt: 16 bytes]
 * [GCM IV / Nonce: 12 bytes]
 * [AES-256-GCM Ciphertext + 128-bit Authentication Tag]
 */
object CryptoBackupEngine {

    private val MAGIC_HEADER = byteArrayOf('F'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION: Byte = 0x01
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 100_000
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Encrypts plaintext bytes using PBKDF2 + AES-256-GCM and writes directly to [outputStream].
     */
    fun encrypt(plaintext: ByteArray, passwordChars: CharArray, outputStream: OutputStream) {
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

        val keySpec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKeyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        // Write Header
        outputStream.write(MAGIC_HEADER)
        outputStream.write(byteArrayOf(FORMAT_VERSION))
        outputStream.write(salt)
        outputStream.write(iv)

        // Write Encrypted Payload + Tag
        val ciphertext = cipher.doFinal(plaintext)
        outputStream.write(ciphertext)
        outputStream.flush()
    }

    /**
     * Reads and decrypts an encrypted backup stream using [passwordChars].
     * Throws an [IllegalArgumentException] or [SecurityException] on invalid password/tampered data.
     */
    fun decrypt(inputStream: InputStream, passwordChars: CharArray): ByteArray {
        val header = ByteArray(4)
        val readHeader = inputStream.read(header)
        if (readHeader != 4 || !header.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("Not a valid Focus Backup archive file.")
        }

        val version = inputStream.read()
        if (version != FORMAT_VERSION.toInt()) {
            throw IllegalArgumentException("Unsupported backup format version: $version")
        }

        val salt = ByteArray(SALT_LENGTH)
        if (inputStream.read(salt) != SALT_LENGTH) {
            throw IllegalArgumentException("Corrupted backup header: incomplete salt.")
        }

        val iv = ByteArray(IV_LENGTH)
        if (inputStream.read(iv) != IV_LENGTH) {
            throw IllegalArgumentException("Corrupted backup header: incomplete IV.")
        }

        val ciphertext = inputStream.readBytes()
        if (ciphertext.isEmpty()) {
            throw IllegalArgumentException("Corrupted backup payload: empty content.")
        }

        val keySpec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKeyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw SecurityException("Incorrect password or corrupted backup file.", e)
        }
    }
}
