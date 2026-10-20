/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
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

        val subKey = com.focusbyrj.app.data.note.ArchiveVaultSecurity.getActiveVaultSubKey()
        val notes = rawNotes.map { rawNote ->
            if (rawNote.isArchived || com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(rawNote)) {
                if (subKey != null) {
                    com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.decryptNotePayload(rawNote, subKey)
                } else {
                    rawNote
                }
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
                put("trashedAt", note.trashedAt ?: JSONObject.NULL)
                put("deletedAt", note.deletedAt ?: JSONObject.NULL)
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
                put("isTrashed", task.isTrashed)
                put("trashedAt", task.trashedAt ?: JSONObject.NULL)
                put("deletedAt", task.deletedAt ?: JSONObject.NULL)
                put("subtasksJson", task.subtasksJson)
                put("planningJson", task.planningJson)
                put("updatedAt", task.updatedAt)
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

            // 1. Pre-validation: parse and validate all notes and tasks before any database mutation
            val hasArchivedNotes = (0 until notesArray.length()).any {
                notesArray.getJSONObject(it).optBoolean("isArchived", false)
            }
            val activeSubKey = com.focusbyrj.app.data.note.ArchiveVaultSecurity.getActiveVaultSubKey()
            if (hasArchivedNotes && activeSubKey == null) {
                throw IllegalStateException("Cannot import vault notes: Secret Archive Vault is locked. Please unlock your secret vault first.")
            }

            val notesToInsert = mutableListOf<NoteEntity>()
            for (i in 0 until notesArray.length()) {
                val nObj = notesArray.getJSONObject(i)
                val isTrashed = nObj.optBoolean("isTrashed", false)
                val trashedAt = if (nObj.has("trashedAt") && !nObj.isNull("trashedAt")) nObj.optLong("trashedAt").takeIf { it > 0L } else null
                val deletedAt = if (nObj.has("deletedAt") && !nObj.isNull("deletedAt")) nObj.optLong("deletedAt").takeIf { it > 0L } else null
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
                    isTrashed = isTrashed,
                    trashedAt = trashedAt,
                    deletedAt = deletedAt,
                    labelsJson = nObj.optString("labelsJson", "[]"),
                    imageUrisJson = nObj.optString("imageUrisJson", "[]"),
                    audioUrisJson = nObj.optString("audioUrisJson", "[]"),
                    createdAt = nObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = nObj.optLong("updatedAt", System.currentTimeMillis())
                )
                if (note.isArchived) {
                    if (activeSubKey == null) {
                        throw SecurityException("Security violation: Vault sub-key unavailable for archived note.")
                    }
                    if (!com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.isVaultEncrypted(note)) {
                        note = com.focusbyrj.app.util.crypto.VaultPayloadEncryptor.encryptNotePayload(note, activeSubKey)
                    }
                }
                notesToInsert.add(note)
            }

            val tasksToInsert = mutableListOf<Task>()
            for (i in 0 until tasksArray.length()) {
                val tObj = tasksArray.getJSONObject(i)
                val typeName = tObj.optString("type", TaskType.TASK.name)
                val recName = tObj.optString("recurrence", RecurrencePattern.NONE.name)
                val isTrashed = tObj.optBoolean("isTrashed", false)
                val trashedAt = if (tObj.has("trashedAt") && !tObj.isNull("trashedAt")) tObj.optLong("trashedAt").takeIf { it > 0L } else null
                val deletedAt = if (tObj.has("deletedAt") && !tObj.isNull("deletedAt")) tObj.optLong("deletedAt").takeIf { it > 0L } else null

                val task = Task(
                    id = tObj.optLong("id", 0L),
                    title = tObj.optString("title", "Untitled Task"),
                    details = tObj.optString("details", ""),
                    dueDate = if (tObj.isNull("dueDate")) null else tObj.optLong("dueDate").takeIf { it > 0L },
                    isCompleted = tObj.optBoolean("isCompleted", false),
                    type = TaskType.values().firstOrNull { it.name.equals(typeName, ignoreCase = true) } ?: TaskType.TASK,
                    recurrence = RecurrencePattern.values().firstOrNull { it.name.equals(recName, ignoreCase = true) } ?: RecurrencePattern.NONE,
                    isPersistent = tObj.optBoolean("isPersistent", false),
                    isPriority = tObj.optBoolean("isPriority", false),
                    completedAt = if (tObj.isNull("completedAt")) null else tObj.optLong("completedAt").takeIf { it > 0L },
                    isTrashed = isTrashed,
                    trashedAt = trashedAt,
                    deletedAt = deletedAt,
                    subtasksJson = tObj.optString("subtasksJson", "[]"),
                    planningJson = tObj.optString("planningJson", "").also { com.focusbyrj.app.data.TaskPlanning.read(it) },
                    updatedAt = tObj.optLong("updatedAt", System.currentTimeMillis())
                )
                tasksToInsert.add(task)
            }

            com.focusbyrj.app.data.TaskPlanning.validateGraph(tasksToInsert)
            require(tasksToInsert.filter { it.id > 0 }.map { it.id }.distinct().size == tasksToInsert.count { it.id > 0 }) { "Duplicate task IDs" }

            // 2. Perform database mutations only AFTER full pre-validation passes
            if (context != null) {
                com.focusbyrj.app.util.backup.DataSafetyManager.writePreOpSnapshot(context, noteDao, "pre_vault_sync_restore")
            }

            if (!mergeMode) {
                noteDao.deleteAllNotes()
            }

            notesToInsert.forEach { noteDao.insertNote(it) }
            taskDao.restoreTasks(tasksToInsert, replace = !mergeMode)

            Result.success(Pair(notesToInsert.size, tasksToInsert.size))
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring vault from JSON", e)
            Result.failure(e)
        }
    }
}
