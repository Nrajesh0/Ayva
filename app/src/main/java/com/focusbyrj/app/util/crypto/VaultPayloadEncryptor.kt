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

import android.util.Base64
import android.util.Log
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteEntity
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Per-Note Zero-Knowledge Payload Cryptography for Secret Vault Notes.
 *
 * Provides cryptographic sub-key encryption for all notes placed inside the secret archive vault.
 * When locked in the vault, note payloads (title, body, checklist, images, audio, labels)
 * are encrypted with a 256-bit AES-GCM subkey derived from the user's PIN via Argon2id.
 *
 * When the vault is locked and subkey is cleared from memory, the database holds zero plaintext.
 *
 * ## Key Versioning
 * Envelope format includes a `kv=<N>` tag to identify which key generation encrypted the note.
 * This allows safe key rotation without permanently losing access to old notes.
 *
 * New format: `ENC_VAULT_V1:kv=<version>:<base64_iv>:<base64_ciphertext>`
 * Legacy format (no kv tag): `ENC_VAULT_V1:<base64_iv>:<base64_ciphertext>` → treated as kv=1
 *
 * ## DecryptionResult
 * Decryption returns a typed [DecryptionResult] instead of silently falling back to the
 * encrypted note. This lets callers surface a meaningful error to the user instead of
 * displaying garbage `🔒 Encrypted Note` content.
 */
object VaultPayloadEncryptor {

    private const val TAG = "VaultPayloadEncryptor"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val ENCRYPTED_PREFIX = "ENC_VAULT_V1:"

    /**
     * Current vault key version. Increment this whenever the vault sub-key derivation
     * parameters change (e.g., new Argon2id parameters, new salt strategy).
     * Stored in [ArchiveVaultSecurity].
     */
    const val CURRENT_KEY_VERSION = 1

    // ──────────────────────────────────────────────────────────────────────
    // Result types
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Typed result for [tryDecryptNotePayload]. Replaces the silent fallback pattern
     * where decryption failure would return the still-encrypted note to the UI.
     */
    sealed class DecryptionResult {
        /** Decryption succeeded. [note] contains plaintext fields. */
        data class Success(val note: NoteEntity) : DecryptionResult()

        /**
         * Decryption failed because the envelope was encrypted with a different key version
         * than the currently loaded vault sub-key.
         * @param envelopeKeyVersion the kv= value from the note's envelope
         * @param currentKeyVersion the key version currently in memory
         */
        data class KeyMismatch(
            val envelopeKeyVersion: Int,
            val currentKeyVersion: Int,
            val noteId: Long
        ) : DecryptionResult()

        /**
         * Decryption failed due to a corrupted envelope (bad base64, wrong IV length,
         * GCM authentication tag failure, malformed JSON).
         */
        data class Corrupted(val noteId: Long, val reason: String) : DecryptionResult()

        /**
         * Vault sub-key is not in memory (vault is locked). Cannot decrypt.
         */
        object VaultLocked : DecryptionResult()

        /** Note is not vault-encrypted — no decryption needed. */
        data class NotEncrypted(val note: NoteEntity) : DecryptionResult()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Checks whether the note's content is encrypted with a vault sub-key.
     */
    fun isVaultEncrypted(note: NoteEntity): Boolean {
        return note.content.startsWith(ENCRYPTED_PREFIX)
    }

    /**
     * Parses the key version from an envelope string.
     * Returns [CURRENT_KEY_VERSION] as the default for legacy envelopes with no kv= tag.
     */
    private fun parseKeyVersion(payload: String): Int {
        // New format: "kv=<N>:<iv>:<ciphertext>"
        if (payload.startsWith("kv=")) {
            val colonIdx = payload.indexOf(':')
            if (colonIdx > 3) {
                return payload.substring(3, colonIdx).toIntOrNull() ?: CURRENT_KEY_VERSION
            }
        }
        // Legacy format: "<iv>:<ciphertext>" — assumed to be key version 1
        return CURRENT_KEY_VERSION
    }

    /**
     * Strips the kv= prefix from the payload to get "<iv>:<ciphertext>".
     */
    private fun stripKeyVersionPrefix(payload: String): String {
        if (payload.startsWith("kv=")) {
            val colonIdx = payload.indexOf(':')
            if (colonIdx > 0) return payload.substring(colonIdx + 1)
        }
        return payload
    }

    // ──────────────────────────────────────────────────────────────────────
    // Encryption
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Encrypts the payload of an archived note using the active Vault Sub-Key.
     * The envelope includes the current [CURRENT_KEY_VERSION] so future decryptors
     * can detect key version mismatches instead of silently failing.
     */
    fun encryptNotePayload(note: NoteEntity, vaultSubKey: ByteArray? = ArchiveVaultSecurity.getActiveVaultSubKey()): NoteEntity {
        if (!note.isArchived || isVaultEncrypted(note) || vaultSubKey == null) {
            return note
        }

        return try {
            val jsonPayload = JSONObject().apply {
                put("title", note.title)
                put("content", note.content)
                put("isChecklist", note.isChecklist)
                put("checklistJson", note.checklistJson)
                put("labelsJson", note.labelsJson)
                put("imageUrisJson", note.imageUrisJson)
                put("audioUrisJson", note.audioUrisJson)
            }.toString().toByteArray(StandardCharsets.UTF_8)

            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val ciphertext = try {
                val keySpec = SecretKeySpec(vaultSubKey, "AES")
                val cipher = Cipher.getInstance(CIPHER_ALGO)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                cipher.doFinal(jsonPayload)
            } finally {
                Arrays.fill(jsonPayload, 0.toByte())
            }

            // Envelope: ENC_VAULT_V1:kv=<version>:<base64_iv>:<base64_ciphertext>
            val envelope = "$ENCRYPTED_PREFIX" +
                    "kv=$CURRENT_KEY_VERSION:" +
                    "${Base64.encodeToString(iv, Base64.NO_WRAP)}:" +
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP)

            // B1-F-007 FIX: Zero ciphertext bytes from heap after Base64 encoding is complete.
            Arrays.fill(ciphertext, 0.toByte())

            note.copy(
                title = "🔒 Encrypted Note",
                content = envelope,
                isChecklist = false,
                checklistJson = "[]",
                labelsJson = "[]",
                imageUrisJson = "[]",
                audioUrisJson = "[]"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt note payload", e)
            throw IllegalStateException("Failed to encrypt archived note payload: ${e.message}", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Decryption — typed result API (preferred)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Typed decryption API. Returns a [DecryptionResult] so callers can handle
     * key mismatches and corrupted envelopes explicitly instead of silently showing
     * encrypted content to the user.
     *
     * @param note The note to decrypt (may or may not be vault-encrypted)
     * @param vaultSubKey The active in-memory vault sub-key; null if vault is locked
     * @param activeKeyVersion The key version associated with [vaultSubKey] (default: [CURRENT_KEY_VERSION])
     */
    fun tryDecryptNotePayload(
        note: NoteEntity,
        vaultSubKey: ByteArray? = ArchiveVaultSecurity.getActiveVaultSubKey(),
        activeKeyVersion: Int = CURRENT_KEY_VERSION
    ): DecryptionResult {
        if (!isVaultEncrypted(note)) return DecryptionResult.NotEncrypted(note)
        if (vaultSubKey == null) return DecryptionResult.VaultLocked

        return try {
            val payload = note.content.removePrefix(ENCRYPTED_PREFIX)
            val envelopeKeyVersion = parseKeyVersion(payload)
            val ivAndCipher = stripKeyVersionPrefix(payload)

            // Key version check: if the envelope was encrypted with a different version
            // of the key, the decryption will fail (GCM auth tag mismatch). Detect this
            // early and return a typed error instead of catching a generic exception.
            if (envelopeKeyVersion != activeKeyVersion) {
                Log.w(TAG, "Note ${note.id}: envelope kv=$envelopeKeyVersion, active kv=$activeKeyVersion — key mismatch")
                return DecryptionResult.KeyMismatch(
                    envelopeKeyVersion = envelopeKeyVersion,
                    currentKeyVersion = activeKeyVersion,
                    noteId = note.id
                )
            }

            val parts = ivAndCipher.split(":", limit = 2)
            if (parts.size != 2) {
                Log.w(TAG, "Malformed encrypted note envelope for note id=${note.id}")
                return DecryptionResult.Corrupted(note.id, "Malformed envelope: expected 2 parts, got ${parts.size}")
            }

            val iv = try { Base64.decode(parts[0], Base64.NO_WRAP) } catch (e: Exception) {
                return DecryptionResult.Corrupted(note.id, "Bad base64 IV: ${e.message}")
            }
            val cipherBytes = try { Base64.decode(parts[1], Base64.NO_WRAP) } catch (e: Exception) {
                return DecryptionResult.Corrupted(note.id, "Bad base64 ciphertext: ${e.message}")
            }

            if (iv.size != GCM_IV_LENGTH) {
                return DecryptionResult.Corrupted(note.id, "IV length mismatch: expected $GCM_IV_LENGTH, got ${iv.size}")
            }

            val keySpec = SecretKeySpec(vaultSubKey, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val decryptedBytes = cipher.doFinal(cipherBytes)
            val obj = try {
                val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
                JSONObject(jsonStr)
            } finally {
                Arrays.fill(decryptedBytes, 0.toByte())
            }

            DecryptionResult.Success(
                note.copy(
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    isChecklist = obj.optBoolean("isChecklist", false),
                    checklistJson = obj.optString("checklistJson", "[]"),
                    labelsJson = obj.optString("labelsJson", "[]"),
                    imageUrisJson = obj.optString("imageUrisJson", "[]"),
                    audioUrisJson = obj.optString("audioUrisJson", "[]")
                )
            )
        } catch (e: javax.crypto.AEADBadTagException) {
            // GCM auth tag failed — key is wrong or ciphertext is tampered
            Log.e(TAG, "GCM auth tag failure for note ${note.id} — key mismatch or tampered ciphertext", e)
            DecryptionResult.Corrupted(note.id, "GCM authentication failed (wrong key or tampered data)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt note payload for note id=${note.id}", e)
            DecryptionResult.Corrupted(note.id, e.message ?: "Unknown decryption error")
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Decryption — legacy compatibility API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Legacy decryption API preserved for backward compatibility with existing callers.
     *
     * Internally delegates to [tryDecryptNotePayload]. On [DecryptionResult.Success],
     * returns the decrypted note. On any failure, logs the error and returns the
     * original (still-encrypted) note — preserving the existing behavior.
     *
     * **Prefer [tryDecryptNotePayload]** for new code so failures can be surfaced to the user.
     */
    fun decryptNotePayload(note: NoteEntity, vaultSubKey: ByteArray? = ArchiveVaultSecurity.getActiveVaultSubKey()): NoteEntity {
        return when (val result = tryDecryptNotePayload(note, vaultSubKey)) {
            is DecryptionResult.Success -> result.note
            is DecryptionResult.NotEncrypted -> result.note
            is DecryptionResult.KeyMismatch -> {
                Log.w(TAG, "decryptNotePayload (legacy): key version mismatch for note ${note.id} " +
                        "(envelope kv=${result.envelopeKeyVersion}, active kv=${result.currentKeyVersion}). " +
                        "Returning encrypted note. Use tryDecryptNotePayload() to handle this case properly.")
                note
            }
            is DecryptionResult.Corrupted -> {
                Log.e(TAG, "decryptNotePayload (legacy): corrupted envelope for note ${note.id}: ${result.reason}. " +
                        "Returning encrypted note.")
                note
            }
            DecryptionResult.VaultLocked -> {
                Log.d(TAG, "decryptNotePayload (legacy): vault is locked for note ${note.id}.")
                note
            }
        }
    }
}
