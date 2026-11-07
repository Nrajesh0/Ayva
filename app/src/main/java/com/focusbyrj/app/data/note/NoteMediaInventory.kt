package com.focusbyrj.app.data.note

import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import org.json.JSONArray
import org.json.JSONObject

/** Strict ownership/backup inventory, independent of the deliberately lenient display parser. */
internal object NoteMediaInventory {
    fun paths(raw: NoteEntity, vaultKey: ByteArray? = null): Set<String> {
        val note = if (VaultPayloadEncryptor.isVaultEncrypted(raw)) {
            val key = vaultKey?.copyOf() ?: error("Vault references are locked")
            try {
                when (val result = VaultPayloadEncryptor.tryDecryptNotePayload(raw, key)) {
                    is VaultPayloadEncryptor.DecryptionResult.Success -> result.note
                    else -> error("Vault references cannot be authenticated")
                }
            } finally { key.fill(0) }
        } else raw
        val paths = linkedSetOf<String>()
        fun add(value: String) { if (value.isNotBlank()) paths.add(value) }
        for (text in listOf(note.imageUrisJson, note.audioUrisJson)) {
            val array = JSONArray(text.ifBlank { "[]" })
            for (i in 0 until array.length()) {
                require(array.get(i) is String) { "Media reference must be a string" }
                add(array.getString(i))
            }
        }
        remapContent(note.content) { add(it); it }
        return paths
    }

    /** Rewrite only URI fields; preserve surrounding text and all other JSON properties. */
    fun remapContent(content: String, transform: (String) -> String): String {
        var output = content
        for ((start, end) in listOf(
            NotesnookBlockManager.BLOCKS_PREFIX to NotesnookBlockManager.BLOCKS_SUFFIX,
            NotesnookBlockManager.ALT_BLOCKS_START to NotesnookBlockManager.ALT_BLOCKS_END)) {
            var from = output.indexOf(start)
            while (from >= 0) {
                val to = output.indexOf(end, from + start.length)
                require(to >= 0) { "Media block metadata is incomplete" }
                val payload = output.substring(from + start.length, to).trim()
                val root: Any = if (payload.startsWith("[")) JSONArray(payload) else JSONObject(payload)
                val blocks = if (root is JSONArray) root else (root as JSONObject).getJSONArray("blocks")
                var changed = false
                for (i in 0 until blocks.length()) {
                    val block = blocks.getJSONObject(i)
                    val type = block.getString("type")
                    require(type in setOf("text", "table", "horizontal_rule", "code", "math", "callout", "quote", "outline", "embed", "attachment", "image")) {
                        "Cannot verify attachments in an unknown note block format"
                    }
                    if (type == "image" || type == "attachment" || block.has("uri")) {
                        require(block.get("uri") is String) { "Media block URI must be a string" }
                        val before = block.getString("uri")
                        val after = if (before.isBlank()) before else transform(before)
                        if (after != before) { block.put("uri", after); changed = true }
                    }
                }
                val replacement = if (changed) root.toString() else output.substring(from + start.length, to)
                if (changed) output = output.substring(0, from + start.length) + replacement + output.substring(to)
                from = output.indexOf(start, from + start.length + replacement.length + end.length)
            }
        }
        return output
    }
}
