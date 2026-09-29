/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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
 */
object CryptoBackupEngine {

    private val MAGIC_HEADER = byteArrayOf('F'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte())

    internal const val FORMAT_VERSION_V1: Byte = 0x01  // PBKDF2-100K (legacy, read-only)
    internal const val FORMAT_VERSION_V2: Byte = 0x02  // Argon2id LOGIN/32MB (legacy, read-only)
    internal const val FORMAT_VERSION_V3: Byte = 0x03  // Argon2id BACKUP/64MB without canary (legacy, read-only)
    internal const val FORMAT_VERSION_V4: Byte = 0x04  // Argon2id BACKUP/64MB with Canary Verifier & Header AAD (current)

    private val VERIFIER_PLAINTEXT = "AYVA_BACKUP_V4_OK".toByteArray(Charsets.UTF_8) // 17 bytes -> padded/exact
    private const val VERIFIER_BLOCK_LENGTH = 33 // 17 bytes plaintext + 16 bytes GCM tag

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
     * Writes the v4 format header directly to [outputStream]:
     *   [Magic: 4B] + [Version: 0x04] + [Salt: 16B] + [Verifier IV: 12B] + [Verifier Block: 33B] + [Payload IV: 12B]
     *
     * Uses Argon2id BACKUP parameters (m=64MB, t=3, p=1) and binds all header metadata via GCM AAD.
     * Streaming ensures zero heap memory buffering, preventing OutOfMemoryError crashes on large media archives.
     */
    fun openEncryptingStream(outputStream: OutputStream, passwordChars: CharArray): OutputStream {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val verifierIv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val payloadIv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        while (verifierIv.contentEquals(payloadIv)) {
            SecureRandom().nextBytes(payloadIv)
        }
        val internalChars = passwordChars.clone()
        var derivedKeyBytes: ByteArray? = null
        try {
            derivedKeyBytes = Argon2idKdf.deriveKey(
                password = internalChars,
                salt = salt,
                params = Argon2idKdf.Parameters.BACKUP
            )

            val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

            // 1. Compute Verifier Canary with AAD binding
            val verifierAad = MAGIC_HEADER + byteArrayOf(FORMAT_VERSION_V4) + salt + verifierIv
            val verifierCipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            verifierCipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, verifierIv))
            verifierCipher.updateAAD(verifierAad)
            val verifierBlock = verifierCipher.doFinal(VERIFIER_PLAINTEXT)

            // 2. Write Complete V4 Header
            outputStream.write(MAGIC_HEADER)
            outputStream.write(byteArrayOf(FORMAT_VERSION_V4))
            outputStream.write(salt)
            outputStream.write(verifierIv)
            outputStream.write(verifierBlock)
            outputStream.write(payloadIv)
            outputStream.flush()

            // 3. Initialize Payload Cipher with full header AAD binding
            val payloadAad = verifierAad + verifierBlock + payloadIv
            val payloadCipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            payloadCipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, payloadIv))
            payloadCipher.updateAAD(payloadAad)

            return javax.crypto.CipherOutputStream(outputStream, payloadCipher)
        } finally {
            derivedKeyBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(internalChars, '\u0000')
        }
    }

    /**
     * Opens a streaming CipherInputStream for decrypting an encrypted backup archive on-the-fly.
     * Supports four format versions:
     *   v4 (0x04): Argon2id BACKUP/64MB with Canary Verifier & Header AAD (current)
     *   v3 (0x03): Argon2id BACKUP/64MB params without canary (legacy backward compat)
     *   v2 (0x02): Argon2id LOGIN/32MB params (legacy backward compat)
     *   v1 (0x01): PBKDF2-100K (oldest legacy backward compat)
     *
     * In V4, password incorrectness is verified immediately via the Canary Verifier block
     * prior to streaming any archive payload.
     *
     * Throws [IllegalArgumentException] for invalid/corrupted headers.
     * Throws [SecurityException] for authentication failures.
     */
    fun openDecryptingStream(inputStream: InputStream, passwordChars: CharArray): InputStream {
        val internalChars = passwordChars.clone()
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
            val isV4 = versionByte == FORMAT_VERSION_V4.toInt()
            val isV3 = versionByte == FORMAT_VERSION_V3.toInt()
            val isV2 = versionByte == FORMAT_VERSION_V2.toInt()
            val isV1 = versionByte == FORMAT_VERSION_V1.toInt()

            if (!isV1 && !isV2 && !isV3 && !isV4) {
                throw IllegalArgumentException("Unsupported backup format version: $versionByte. Expected 1, 2, 3, or 4.")
            }

            val salt = ByteArray(SALT_LENGTH)
            if (!readFully(inputStream, salt)) {
                throw IllegalArgumentException("Corrupted backup header: incomplete salt.")
            }

            if (isV4) {
                val verifierIv = ByteArray(IV_LENGTH)
                if (!readFully(inputStream, verifierIv)) {
                    throw IllegalArgumentException("Corrupted backup header: incomplete verifier IV.")
                }

                val verifierBlock = ByteArray(VERIFIER_BLOCK_LENGTH)
                if (!readFully(inputStream, verifierBlock)) {
                    throw IllegalArgumentException("Corrupted backup header: incomplete verifier block.")
                }

                val payloadIv = ByteArray(IV_LENGTH)
                if (!readFully(inputStream, payloadIv)) {
                    throw IllegalArgumentException("Corrupted backup header: incomplete payload IV.")
                }

                if (verifierIv.contentEquals(payloadIv)) {
                    throw SecurityException("Corrupted backup header: verifier and payload IVs cannot be identical.")
                }

                derivedKeyBytes = Argon2idKdf.deriveKey(
                    password = internalChars,
                    salt = salt,
                    params = Argon2idKdf.Parameters.BACKUP
                )

                val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

                // Authenticate and verify the Canary block
                val verifierAad = MAGIC_HEADER + byteArrayOf(FORMAT_VERSION_V4) + salt + verifierIv
                val verifierCipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                verifierCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, verifierIv))
                verifierCipher.updateAAD(verifierAad)

                val decryptedVerifier = try {
                    verifierCipher.doFinal(verifierBlock)
                } catch (e: Exception) {
                    throw SecurityException("Incorrect backup password. Please check your password and try again.", e)
                }

                if (!decryptedVerifier.contentEquals(VERIFIER_PLAINTEXT)) {
                    throw SecurityException("Incorrect backup password. Please check your password and try again.")
                }

                // Verifier passed! Initialize payload cipher with full header AAD
                val payloadAad = verifierAad + verifierBlock + payloadIv
                val payloadCipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                payloadCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, payloadIv))
                payloadCipher.updateAAD(payloadAad)

                return javax.crypto.CipherInputStream(inputStream, payloadCipher)
            }

            // Legacy formats V1, V2, V3
            val iv = ByteArray(IV_LENGTH)
            if (!readFully(inputStream, iv)) {
                throw IllegalArgumentException("Corrupted backup header: incomplete IV.")
            }

            derivedKeyBytes = when {
                isV3 -> {
                    // v3: Argon2id BACKUP params (64MB)
                    Argon2idKdf.deriveKey(
                        password = internalChars,
                        salt = salt,
                        params = Argon2idKdf.Parameters.BACKUP
                    )
                }
                isV2 -> {
                    // v2: Argon2id LOGIN params (32MB)
                    Argon2idKdf.deriveKey(
                        password = internalChars,
                        salt = salt,
                        params = Argon2idKdf.Parameters.LOGIN
                    )
                }
                else -> {
                    // v1: Legacy PBKDF2
                    val keySpec = javax.crypto.spec.PBEKeySpec(internalChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
                    try {
                        val keyFactory = javax.crypto.SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                        keyFactory.generateSecret(keySpec).encoded
                    } finally {
                        keySpec.clearPassword()
                    }
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
            Arrays.fill(internalChars, '\u0000')
        }
    }

    /**
     * Encrypts plaintext bytes using Argon2id + AES-256-GCM and writes to [outputStream].
     * Writes a v4 format header (64MB BACKUP Argon2id + Verifier Canary). Uses streaming cipher internally.
     */
    fun encrypt(plaintext: ByteArray, passwordChars: CharArray, outputStream: OutputStream) {
        val passwordCopy = passwordChars.clone()
        try {
            openEncryptingStream(outputStream, passwordCopy).use { cipherOut ->
                cipherOut.write(plaintext)
                cipherOut.flush()
            }
        } finally {
            // Zero only internal clone; caller manages their own CharArray lifecycle
            Arrays.fill(passwordCopy, '\u0000')
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
            // Zero only internal clone; caller manages their own CharArray lifecycle
            Arrays.fill(passwordCopy, '\u0000')
        }
    }
}
