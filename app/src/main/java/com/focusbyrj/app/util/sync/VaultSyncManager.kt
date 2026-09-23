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

import android.content.Context
import android.util.Log
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskDao
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.note.NoteDao
import com.focusbyrj.app.data.note.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages full Vault bundling, serialization, and import restoration for Notes and Tasks.
 * Matches 100% with the Notesnook-inspired web application schema.
 */
object VaultSyncManager {

    private const val TAG = "VaultSyncManager"

    data class VaultPayload(
        val notes: List<NoteEntity>,
        val tasks: List<Task>,
        val exportedAt: Long = System.currentTimeMillis(),
        val appVersion: String = "1.9.0"
    )

    /**
     * Serializes all notes and tasks into a unified JSON string.
     * Fails if any secret vault notes exist while the secret vault is locked, preventing unrecoverable backups.
     */
    suspend fun createVaultJson(noteDao: NoteDao, taskDao: TaskDao): String = withContext(Dispatchers.IO) {
        val rawNotes = noteDao.getAllNotesList()
        val hasLockedVaultNotes = rawNotes.any { com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(it) } &&
                com.focusbyrj.app.data.note.ArchiveVaultSecurity.getActiveVaultSubKey() == null
        if (hasLockedVaultNotes) {
            throw IllegalStateException("Cannot export vault: Secret Archive Vault is locked. Please unlock your secret vault first.")
        }

        val notes = rawNotes.map { rawNote ->
            if (rawNote.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(rawNote)) {
                com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(rawNote)
            } else {
                rawNote
            }
        }
        val tasks = taskDao.getAllTasksList()

        val root = JSONObject()
        root.put("app", "FocusByRj")
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val notesArray = JSONArray()
        notes.forEach { note ->
            val nObj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("isChecklist", note.isChecklist)
                put("checklistJson", note.checklistJson)
                put("colorKey", note.colorKey)
                put("fontKey", note.fontKey)
                put("isPinned", note.isPinned)
                put("isArchived", note.isArchived)
                put("isTrashed", note.isTrashed)
                put("labelsJson", note.labelsJson)
                put("imageUrisJson", note.imageUrisJson)
                put("audioUrisJson", note.audioUrisJson)
                put("createdAt", note.createdAt)
                put("updatedAt", note.updatedAt)
            }
            notesArray.put(nObj)
        }
        root.put("notes", notesArray)

        val tasksArray = JSONArray()
        tasks.forEach { task ->
            val tObj = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("details", task.details)
                put("dueDate", task.dueDate ?: JSONObject.NULL)
                put("isCompleted", task.isCompleted)
                put("type", task.type.name)
                put("recurrence", task.recurrence.name)
                put("isPersistent", task.isPersistent)
                put("isPriority", task.isPriority)
                put("completedAt", task.completedAt ?: JSONObject.NULL)
            }
            tasksArray.put(tObj)
        }
        root.put("tasks", tasksArray)

        return@withContext root.toString(2)
    }

    /**
     * Restores notes and tasks from a raw decrypted JSON payload.
     */
    suspend fun restoreVaultFromJson(
        jsonString: String,
        noteDao: NoteDao,
        taskDao: TaskDao,
        mergeMode: Boolean = true,
        context: Context? = null
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val notesArray = root.optJSONArray("notes") ?: JSONArray()
            val tasksArray = root.optJSONArray("tasks") ?: JSONArray()

            var importedNotesCount = 0
            var importedTasksCount = 0

            // If replace mode (mergeMode == false), wipe existing local records first
            if (!mergeMode) {
                noteDao.deleteAllNotes()
                taskDao.deleteAllTasks()
            }

            // Restore Notes
            for (i in 0 until notesArray.length()) {
                val nObj = notesArray.getJSONObject(i)
                var note = NoteEntity(
                    id = if (mergeMode) 0L else nObj.optLong("id", 0L),
                    title = nObj.optString("title", ""),
                    content = nObj.optString("content", ""),
                    isChecklist = nObj.optBoolean("isChecklist", false),
                    checklistJson = nObj.optString("checklistJson", "[]"),
                    colorKey = nObj.optString("colorKey", "default"),
                    fontKey = nObj.optString("fontKey", "default"),
                    isPinned = nObj.optBoolean("isPinned", false),
                    isArchived = nObj.optBoolean("isArchived", false),
                    isTrashed = nObj.optBoolean("isTrashed", false),
                    labelsJson = nObj.optString("labelsJson", "[]"),
                    imageUrisJson = nObj.optString("imageUrisJson", "[]"),
                    audioUrisJson = nObj.optString("audioUrisJson", "[]"),
                    createdAt = nObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = nObj.optLong("updatedAt", System.currentTimeMillis())
                )
                if (note.isArchived) {
                    note = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(note)
                }
                noteDao.insertNote(note)
                importedNotesCount++
            }

            // Restore Tasks
            for (i in 0 until tasksArray.length()) {
                val tObj = tasksArray.getJSONObject(i)
                val typeName = tObj.optString("type", TaskType.TASK.name)
                val recName = tObj.optString("recurrence", RecurrencePattern.NONE.name)

                val task = Task(
                    id = if (mergeMode) 0L else tObj.optLong("id", 0L),
                    title = tObj.optString("title", "Untitled Task"),
                    details = tObj.optString("details", ""),
                    dueDate = if (tObj.isNull("dueDate")) null else tObj.optLong("dueDate").takeIf { it > 0L },
                    isCompleted = tObj.optBoolean("isCompleted", false),
                    type = try { TaskType.valueOf(typeName) } catch (_: Exception) { TaskType.TASK },
                    recurrence = try { RecurrencePattern.valueOf(recName) } catch (_: Exception) { RecurrencePattern.NONE },
                    isPersistent = tObj.optBoolean("isPersistent", false),
                    isPriority = tObj.optBoolean("isPriority", false),
                    completedAt = if (tObj.isNull("completedAt")) null else tObj.optLong("completedAt").takeIf { it > 0L }
                )
                taskDao.insertTask(task)
                importedTasksCount++
            }

            if (context != null) {
                com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context.applicationContext)
            }

            Result.success(Pair(importedNotesCount, importedTasksCount))
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring vault from JSON", e)
            Result.failure(e)
        }
    }
}
