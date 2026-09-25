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

package com.focusbyrj.app.data.note

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "keep_notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val isChecklist: Boolean = false,
    val checklistJson: String = "[]",
    val colorKey: String = "default",
    val fontKey: String = "default",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val labelsJson: String = "[]",
    val imageUrisJson: String = "[]",
    val audioUrisJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Timestamp (epoch ms) when this note was moved to the trash.
     * Used for 30-day auto-purge and pre-destructive-operation snapshots.
     * Null if the note has never been trashed.
     */
    val trashedAt: Long? = null,
    /**
     * Soft-deletion audit log timestamp. Set when the note is permanently
     * deleted (hard deleted from DB) — recorded in the deletion log before
     * the row is actually removed, giving a recovery window.
     * Null for active / trashed notes.
     */
    val deletedAt: Long? = null,
    /**
     * Flag indicating this is a dedicated long-form Story / Article,
     * segregating it from quick sticky notes in Tab 3 into the Stories Studio (Tab 4).
     */
    val isArticle: Boolean = false,
    /** Subtitle or editorial standfirst / deck for blog posts and articles. */
    val subtitle: String = "",
    /** High-resolution hero cover image URI for the story header. */
    val coverImageUri: String? = null,
    /** Status flag: false = Draft (WIP), true = Published story. */
    val isPublished: Boolean = false,
    /** Timestamp (epoch ms) when this article was published. */
    val publishedAt: Long? = null,
    /** Estimated reading time in minutes computed from word count. */
    val readingTimeMinutes: Int = 0
) {
    fun getImageUris(): List<String> {
        if (imageUrisJson.isBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(imageUrisJson)
            for (i in 0 until jsonArray.length()) {
                val uri = jsonArray.getString(i).trim()
                if (uri.isNotEmpty() && !list.contains(uri)) {
                    list.add(uri)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getAudioUris(): List<String> {
        if (audioUrisJson.isBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(audioUrisJson)
            for (i in 0 until jsonArray.length()) {
                val uri = jsonArray.getString(i).trim()
                if (uri.isNotEmpty() && !list.contains(uri)) {
                    list.add(uri)
                }
            }
        } catch (_: Exception) {}
        return list
    }
    fun getChecklistItems(): List<ChecklistItem> {
        if (!isChecklist || checklistJson.isBlank()) return emptyList()
        val list = mutableListOf<ChecklistItem>()
        try {
            val jsonArray = JSONArray(checklistJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ChecklistItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        isChecked = obj.optBoolean("isChecked", false)
                    )
                )
            }
        } catch (_: Exception) {
            // Fallback: parse lines from content if json corrupted
            if (content.isNotBlank()) {
                content.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        list.add(ChecklistItem(text = line, isChecked = false))
                    }
                }
            }
        }
        return list
    }

    fun getLabels(): List<String> {
        if (labelsJson.isBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(labelsJson)
            for (i in 0 until jsonArray.length()) {
                val label = jsonArray.getString(i).trim()
                if (label.isNotEmpty() && !list.contains(label)) {
                    list.add(label)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun isEmptyNote(): Boolean {
        if (title.isNotBlank()) return false
        if (subtitle.isNotBlank()) return false
        if (!coverImageUri.isNullOrBlank()) return false
        if (getImageUris().isNotEmpty()) return false
        if (getAudioUris().isNotEmpty()) return false
        if (getLabels().isNotEmpty()) return false
        if (isChecklist) {
            val items = getChecklistItems()
            return items.none { it.text.isNotBlank() }
        }
        // For block-serialized content (<!--NOTESNOOK_BLOCKS:{...}-->), treat a note as empty
        // only when every block is a Text block with blank text. Non-text blocks (Table, Code,
        // Image, Callout, etc.) are always considered real content.
        val blockPrefix = "<!--NOTESNOOK_BLOCKS:"
        val prefixIdx = content.indexOf(blockPrefix)
        if (prefixIdx != -1) {
            val suffixIdx = content.indexOf(":BLOCKS_END-->", prefixIdx + blockPrefix.length).takeIf { it != -1 }
                ?: content.indexOf("-->", prefixIdx + blockPrefix.length)
            if (suffixIdx != -1) {
                return try {
                    var jsonStr = content.substring(prefixIdx + blockPrefix.length, suffixIdx).trim()
                    if (jsonStr.endsWith(":BLOCKS_END")) {
                        jsonStr = jsonStr.removeSuffix(":BLOCKS_END").trim()
                    }
                    val root = JSONObject(jsonStr)
                    val blocks = root.optJSONArray("blocks") ?: return true
                    var allTextEmpty = true
                    for (i in 0 until blocks.length()) {
                        val block = blocks.getJSONObject(i)
                        val type = block.optString("type", "text")
                        if (type != "text") {
                            // Any non-text block (table, code, image, etc.) counts as content
                            allTextEmpty = false
                            break
                        }
                        if (block.optString("text", "").isNotBlank()) {
                            allTextEmpty = false
                            break
                        }
                    }
                    allTextEmpty
                } catch (_: Exception) {
                    content.isBlank()
                }
            }
        }
        return content.isBlank()
    }
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecked: Boolean = false
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("text", text)
            put("isChecked", isChecked)
        }
    }

    companion object {
        fun listToJson(items: List<ChecklistItem>): String {
            val jsonArray = JSONArray()
            items.forEach { jsonArray.put(it.toJsonObject()) }
            return jsonArray.toString()
        }

        fun listFromJson(json: String): List<ChecklistItem> {
            if (json.isBlank()) return emptyList()
            val list = mutableListOf<ChecklistItem>()
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ChecklistItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.optString("text", ""),
                            isChecked = obj.optBoolean("isChecked", false)
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }
    }
}
