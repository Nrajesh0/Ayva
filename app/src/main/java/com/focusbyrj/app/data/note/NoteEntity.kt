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
    val updatedAt: Long = System.currentTimeMillis()
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
        if (getImageUris().isNotEmpty()) return false
        if (getAudioUris().isNotEmpty()) return false
        if (getLabels().isNotEmpty()) return false
        if (isChecklist) {
            val items = getChecklistItems()
            return items.none { it.text.isNotBlank() }
        } else {
            return content.isBlank()
        }
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
