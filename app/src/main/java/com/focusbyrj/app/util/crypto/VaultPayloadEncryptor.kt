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
 */
object VaultPayloadEncryptor {

    private const val TAG = "VaultPayloadEncryptor"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val ENCRYPTED_PREFIX = "ENC_VAULT_V1:"

    /**
     * Checks whether the note's content is encrypted with a vault sub-key.
     */
    fun isVaultEncrypted(note: NoteEntity): Boolean {
        return note.content.startsWith(ENCRYPTED_PREFIX)
    }

    /**
     * Encrypts the payload of an archived note using the active Vault Sub-Key.
     */
    fun encryptNotePayload(note: NoteEntity, vaultSubKey: ByteArray? = ArchiveVaultSecurity.getActiveVaultSubKey()): NoteEntity {
        if (!note.isArchived || vaultSubKey == null || isVaultEncrypted(note)) {
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

            val keySpec = SecretKeySpec(vaultSubKey, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val ciphertext = cipher.doFinal(jsonPayload)
            val envelope = "$ENCRYPTED_PREFIX${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"

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
            note
        }
    }

    /**
     * Decrypts the payload of an archived note in memory using the active Vault Sub-Key.
     */
    fun decryptNotePayload(note: NoteEntity, vaultSubKey: ByteArray? = ArchiveVaultSecurity.getActiveVaultSubKey()): NoteEntity {
        if (!isVaultEncrypted(note)) {
            return note
        }
        if (vaultSubKey == null) {
            return note
        }

        return try {
            val payload = note.content.removePrefix(ENCRYPTED_PREFIX)
            val parts = payload.split(":")
            if (parts.size != 2) return note

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

            val keySpec = SecretKeySpec(vaultSubKey, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
            val obj = JSONObject(jsonStr)

            note.copy(
                title = obj.optString("title", ""),
                content = obj.optString("content", ""),
                isChecklist = obj.optBoolean("isChecklist", false),
                checklistJson = obj.optString("checklistJson", "[]"),
                labelsJson = obj.optString("labelsJson", "[]"),
                imageUrisJson = obj.optString("imageUrisJson", "[]"),
                audioUrisJson = obj.optString("audioUrisJson", "[]")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt note payload with vault subkey", e)
            note
        }
    }
}
