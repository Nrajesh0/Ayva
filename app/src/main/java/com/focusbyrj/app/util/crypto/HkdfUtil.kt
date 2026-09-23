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

import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF (HMAC-based Extract-and-Expand Key Derivation Function) — RFC 5869.
 *
 * Used to derive multiple cryptographically independent subkeys from a single master key.
 * This is the key-separation layer that prevents a leak of one derived key from
 * compromising any other derived key.
 *
 * Domain separation is enforced by distinct [info] strings per derived key:
 * - "ayva_auth_v1"  → Auth token sent to Supabase (never used for decryption)
 * - "ayva_vault_v1" → Data Encryption Key (never sent to server)
 * - "ayva_hmac_v1"  → HMAC signing key (never sent to server)
 *
 * Even if an attacker brute-forces the Supabase auth password hash and recovers the
 * authToken, they cannot reverse HKDF to obtain the userKey or hmacKey.
 */
object HkdfUtil {

    private const val HMAC_SHA256 = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 output length in bytes

    /**
     * HKDF-Extract: compresses input key material (IKM) and salt into a pseudorandom key (PRK).
     *
     * When using Argon2id as the upstream KDF, this step is optional (Argon2id output is already
     * uniformly distributed), but is included for strict RFC 5869 compliance.
     *
     * @param salt Optional salt (if null, uses a zero-filled byte array of hash length).
     * @param ikm  Input key material (the Argon2id master key bytes).
     * @return PRK — 32 bytes of pseudorandom key material.
     */
    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val effectiveSalt = salt ?: ByteArray(HASH_LEN) // zero-filled per RFC 5869 §2.2
        return hmacSha256(effectiveSalt, ikm)
    }

    /**
     * HKDF-Expand: expands a PRK and an [info] context string into [length] bytes of output key material.
     *
     * @param prk    Pseudorandom key (output of HKDF-Extract, or the Argon2id master key directly).
     * @param info   A context string that domain-separates the derived key. Must be unique per key purpose.
     * @param length Desired output key length in bytes. Must be ≤ 255 × 32 (8160 bytes).
     * @return Output Key Material (OKM) of exactly [length] bytes.
     */
    fun expand(prk: ByteArray, info: String, length: Int): ByteArray {
        require(length > 0 && length <= 255 * HASH_LEN) {
            "HKDF expand length must be between 1 and ${255 * HASH_LEN} bytes, got $length"
        }

        val infoBytes = info.toByteArray(StandardCharsets.UTF_8)
        val n = (length + HASH_LEN - 1) / HASH_LEN // number of blocks needed

        val okm = ByteArray(n * HASH_LEN)
        var t = ByteArray(0) // T(0) = empty string

        for (i in 1..n) {
            // T(i) = HMAC-SHA256(PRK, T(i-1) || info || i)
            val hmacInput = ByteArray(t.size + infoBytes.size + 1)
            System.arraycopy(t, 0, hmacInput, 0, t.size)
            System.arraycopy(infoBytes, 0, hmacInput, t.size, infoBytes.size)
            hmacInput[t.size + infoBytes.size] = i.toByte()

            t = hmacSha256(prk, hmacInput)
            System.arraycopy(t, 0, okm, (i - 1) * HASH_LEN, t.size)
        }

        return okm.copyOf(length)
    }

    /**
     * Convenience function: runs both HKDF-Extract and HKDF-Expand in one call.
     *
     * @param ikm    Input key material (master key, e.g., Argon2id output).
     * @param info   Domain-separation context string (unique per derived key purpose).
     * @param length Output key length in bytes.
     * @param salt   Optional salt for the extract step. Defaults to null (RFC 5869 §2.2 default).
     * @return Derived subkey of [length] bytes.
     */
    fun deriveKey(ikm: ByteArray, info: String, length: Int = 32, salt: ByteArray? = null): ByteArray {
        val prk = extract(salt, ikm)
        return try {
            expand(prk, info, length)
        } finally {
            Arrays.fill(prk, 0.toByte())
        }
    }

    // =========================================================================
    // Internal Helpers
    // =========================================================================

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }

    /**
     * Computes an HMAC-SHA256 message authentication code.
     * Used externally by SupabaseKeyManager for item-level anti-tamper signatures.
     *
     * @param key  The HMAC signing key (hmacKey derived via HKDF).
     * @param data The message bytes to authenticate.
     * @return 32-byte HMAC-SHA256 tag.
     */
    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        return hmacSha256(key, data)
    }

    /**
     * Constant-time comparison of two HMAC tags. Prevents timing-oracle attacks.
     *
     * @return true if both arrays are equal in length and content.
     */
    fun hmacEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
