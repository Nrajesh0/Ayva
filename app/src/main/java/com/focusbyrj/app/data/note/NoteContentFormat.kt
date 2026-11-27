/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

/** Persisted block delimiters shared by the editor and strict media inventory. */
internal object NoteContentFormat {
    const val BLOCKS_PREFIX = "<!--NOTESNOOK_BLOCKS:"
    const val BLOCKS_SUFFIX = ":BLOCKS_END-->"
    const val ALT_BLOCKS_START = "<!--BLOCKS_START-->"
    const val ALT_BLOCKS_END = "<!--BLOCKS_END-->"

    /** A marker inside a quoted block value is ordinary note text. */
    fun endOfPayload(content: String, from: Int, suffix: String = BLOCKS_SUFFIX): Int {
        var quoted = false
        var escaped = false
        for (index in from until content.length) {
            val char = content[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else if (char == '"') {
                quoted = true
            } else if (content.startsWith(suffix, index) ||
                suffix == BLOCKS_SUFFIX && content.startsWith("-->", index)) {
                return index
            }
        }
        return -1
    }
    /** Merge former v6 columns without discarding the body, embedded blocks or media. */
    fun applyLegacyArticle(note: NoteEntity, metadata: org.json.JSONObject): NoteEntity {
        val images = org.json.JSONArray(note.imageUrisJson)
        val cover = metadata.optString("coverImageUri")
        if (cover.isNotBlank() && (0 until images.length()).none { images.optString(it) == cover }) images.put(cover)
        val body = note.content
        val from = body.indexOf(BLOCKS_PREFIX)
        val end = if (from < 0) -1 else endOfPayload(body, from + BLOCKS_PREFIX.length)
        val root = if (end >= 0) {
            val payload = body.substring(from + BLOCKS_PREFIX.length, end).trim()
            if (payload.startsWith("[")) org.json.JSONObject().put("blocks", org.json.JSONArray(payload))
            else org.json.JSONObject(payload)
        } else org.json.JSONObject().put("version", 1).put("blocks", org.json.JSONArray().put(
            org.json.JSONObject().put("type", "text").put("text", body)))
        for (field in listOf("isArticle", "subtitle", "isPublished", "publishedAt", "readingTimeMinutes")) {
            if (metadata.has(field)) root.put(field, metadata.get(field))
        }
        val document = BLOCKS_PREFIX + root + BLOCKS_SUFFIX
        val merged = if (end < 0) document else {
            val length = if (body.startsWith(BLOCKS_SUFFIX, end)) BLOCKS_SUFFIX.length else 3
            body.replaceRange(from, end + length, document)
        }
        return note.copy(content = merged, imageUrisJson = images.toString(), legacyArticleMetadata = null)
    }

}
