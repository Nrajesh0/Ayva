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

package com.focusbyrj.app.util.sync

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Knowledge Cryptography Engine for Multi-Device Sync and Vault Packages.
 *
 * Fully compliant with WebCrypto (window.crypto.subtle) standard:
 * - Algorithm: AES-256-GCM
 * - Key Derivation: PBKDF2WithHmacSHA256 (100,000 iterations, 128-bit salt)
 * - Nonce/IV: 96-bit (12-byte) cryptographically secure random per encryption
 * - Tag Length: 128-bit authentication tag
 */
object VaultCryptoEngine {

    private const val TAG = "VaultCryptoEngine"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    data class EncryptedPackage(
        val saltBase64: String,
        val ivBase64: String,
        val ciphertextBase64: String,
        val version: Int = 1,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun toJsonString(): String {
            val json = JSONObject()
            val meta = JSONObject().apply {
                put("version", version)
                put("cipher", "AES-256-GCM")
                put("kdf", "PBKDF2-SHA256")
                put("iterations", PBKDF2_ITERATIONS)
                put("createdAt", createdAt)
            }
            val payload = JSONObject().apply {
                put("salt", saltBase64)
                put("iv", ivBase64)
                put("ciphertext", ciphertextBase64)
            }
            json.put("metadata", meta)
            json.put("payload", payload)
            return json.toString(2)
        }

        companion object {
            fun fromJsonString(jsonStr: String): EncryptedPackage? {
                return try {
                    val root = JSONObject(jsonStr)
                    val payload = root.getJSONObject("payload")
                    val meta = root.optJSONObject("metadata")
                    EncryptedPackage(
                        saltBase64 = payload.getString("salt"),
                        ivBase64 = payload.getString("iv"),
                        ciphertextBase64 = payload.getString("ciphertext"),
                        version = meta?.optInt("version", 1) ?: 1,
                        createdAt = meta?.optLong("createdAt", System.currentTimeMillis()) ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse EncryptedPackage JSON", e)
                    null
                }
            }
        }
    }

    /**
     * Derives a 256-bit AES Key from a user passphrase and cryptographic salt.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext string into an EncryptedPackage using AES-256-GCM.
     */
    fun encrypt(plaintext: String, passphrase: CharArray): EncryptedPackage {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(AES_GCM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        return EncryptedPackage(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts an EncryptedPackage back into the original plaintext.
     */
    fun decrypt(pkg: EncryptedPackage, passphrase: CharArray): Result<String> {
        return try {
            val salt = Base64.decode(pkg.saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(pkg.ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(pkg.ciphertextBase64, Base64.NO_WRAP)

            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            Result.success(String(decryptedBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error: invalid passphrase or corrupted payload", e)
            Result.failure(e)
        }
    }

    /**
     * BIP-39 style wordlist helper for generating mnemonic backup phrases.
     */
    val SAMPLE_WORDLIST = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse",
        "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire", "across", "act",
        "action", "actor", "actress", "actual", "adapt", "add", "addict", "address", "adjust", "admit",
        "adult", "advance", "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
        "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album", "alcohol", "alert",
        "alien", "all", "alley", "allow", "almost", "alone", "alpha", "already", "also", "alter",
        "always", "amateur", "amazing", "among", "amount", "amused", "analyst", "anchor", "ancient", "anger",
        "angle", "angry", "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
        "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april", "arch", "arctic",
        "area", "arena", "argue", "arm", "armed", "armor", "army", "around", "arrange", "arrest",
        "arrive", "arrow", "art", "artefact", "artist", "artwork", "ask", "aspect", "assault", "asset",
        "assist", "assume", "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction",
        "audit", "august", "aunt", "author", "auto", "autumn", "average", "avocado", "avoid", "awake",
        "aware", "away", "awesome", "awful", "awkward", "axis", "baby", "bachelor", "bacon", "badge",
        "bag", "balance", "balcony", "ball", "bamboo", "banana", "banner", "bar", "barely", "bargain",
        "barrel", "base", "basic", "basket", "battle", "beach", "bean", "beauty", "because", "become"
    )

    fun generate12WordMnemonic(): List<String> {
        val random = SecureRandom()
        val words = mutableListOf<String>()
        val listSize = SAMPLE_WORDLIST.size
        for (i in 0 until 12) {
            val idx = random.nextInt(listSize)
            words.add(SAMPLE_WORDLIST[idx])
        }
        return words
    }
}
