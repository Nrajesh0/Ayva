package com.focusbyrj.app.util.backup

import org.json.JSONObject

/** Export and restore share limits so a successful export cannot exceed its reader's bounds. */
internal object BackupArchiveLimits {
    const val MANIFEST_BYTES = 50L * 1024 * 1024
    const val MEDIA_ENTRY_BYTES = 250L * 1024 * 1024
    const val TOTAL_MEDIA_BYTES = 500L * 1024 * 1024
    const val ENTRIES = 20_000
    const val ENCRYPTED_BYTES = 1_000_000_000L

    fun parseManifest(text: String): JSONObject {
        checkJsonNesting(text)
        try { return JSONObject(text) }
        catch (_: org.json.JSONException) { throw IllegalArgumentException("Invalid data.json backup manifest.") }
    }

    fun checkJsonNesting(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) { "Backup manifest nesting exceeds its safety limit." } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid data.json backup manifest." } }
            }
        }
        require(!quoted && depth == 0) { "Invalid data.json backup manifest." }
    }

    fun checkEmbeddedJson(text: String) {
        val value = text.trimStart()
        if (value.startsWith('{') || value.startsWith('[')) checkJsonNesting(value)
    }

    fun checkNoteContent(text: String) {
        val formats = listOf("<!--NOTESNOOK_BLOCKS:" to ":BLOCKS_END-->", "<!--BLOCKS_START-->" to "<!--BLOCKS_END-->")
        for ((prefix, suffix) in formats) {
            var start = text.indexOf(prefix)
            while (start >= 0) {
                val from = start + prefix.length
                val end = com.focusbyrj.app.data.note.NoteContentFormat.endOfPayload(text, from, suffix)
                require(end >= from) { "Incomplete note block metadata in backup." }
                checkEmbeddedJson(text.substring(from, end))
                val length = if (text.startsWith(suffix, end)) suffix.length else 3
                start = text.indexOf(prefix, end + length)
            }
        }
    }

    fun checkExport(manifestBytes: Long, mediaSizes: List<Long>) {
        require(manifestBytes in 0..MANIFEST_BYTES) { "Backup manifest exceeds the supported restore limit (50MB). Existing backups were kept." }
        require(mediaSizes.size < ENTRIES) { "Backup contains too many attachments for a complete restore." }
        var total = 0L
        for (size in mediaSizes) {
            require(size in 0..MEDIA_ENTRY_BYTES) { "An attachment exceeds the supported restore limit (250MB). Existing backups were kept." }
            total += size
            require(total <= TOTAL_MEDIA_BYTES) { "Attachments exceed the supported restore limit (500MB total). Existing backups were kept." }
        }
    }
}
