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

package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusbyrj.app.R
import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import com.focusbyrj.app.util.sync.supabase.AutoSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Unexported BroadcastReceiver dedicated to handling internal user interaction clicks
 * from the Notes home screen widget (next/prev note, filter cycling, checklist item toggling/removal).
 * Segregating these actions from the exported NoteWidgetProvider blocks arbitrary third-party apps
 * from injecting unauthorized broadcasts to delete or mutate note checklist items.
 */
class NoteWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        when (intent.action) {
            NoteWidgetProvider.ACTION_TOGGLE_ITEM -> {
                val actionType = intent.getStringExtra(NoteWidgetProvider.EXTRA_ACTION_TYPE) ?: NoteWidgetProvider.ACTION_TYPE_TOGGLE
                val noteId = intent.getLongExtra(NoteWidgetProvider.EXTRA_NOTE_ID, -1L)
                val itemId = intent.getStringExtra(NoteWidgetProvider.EXTRA_ITEM_ID)

                if (actionType == NoteWidgetProvider.ACTION_TYPE_EDIT) {
                    if (noteId != -1L) {
                        val editIntent = Intent(context, QuickEditNoteActivity::class.java).apply {
                            putExtra(QuickEditNoteActivity.EXTRA_NOTE_ID, noteId)
                            if (!itemId.isNullOrBlank()) {
                                putExtra(QuickEditNoteActivity.EXTRA_TARGET_ITEM_ID, itemId)
                            }
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val options = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        } else {
                            null
                        }
                        context.startActivity(editIntent, options?.toBundle())
                    }
                } else if (actionType == NoteWidgetProvider.ACTION_TYPE_REMOVE) {
                    if (noteId != -1L && !itemId.isNullOrBlank()) {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val noteDao = NoteDatabase.getInstance(context).noteDao()
                                val cached = NotesViewModel.latestNotesCache[noteId]
                                val dbNote = noteDao.getNoteByIdSync(noteId)
                                val note = when {
                                    cached != null && dbNote != null -> if (cached.updatedAt >= dbNote.updatedAt) cached else dbNote
                                    dbNote != null -> dbNote
                                    cached != null -> cached
                                    else -> null
                                }
                                if (note != null && note.isChecklist && !note.isArchived) {
                                    val remainingItems = note.getChecklistItems().filterNot { it.id == itemId }
                                    val (uncompleted, completed) = remainingItems.partition { !it.isChecked }
                                    val updatedNote = note.copy(
                                        checklistJson = ChecklistItem.listToJson(uncompleted + completed),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    noteDao.updateNote(updatedNote)
                                    NotesViewModel.latestNotesCache[updatedNote.id] = updatedNote
                                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                        NoteWidgetConfigHelper.setCurrentNoteId(context, appWidgetId, updatedNote.id)
                                    }

                                    AutoSyncManager.triggerDebouncedSync(context)
                                    NoteWidgetProvider.updateAllWidgets(context)
                                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                        NoteWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                                    }
                                }
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                } else {
                    if (noteId != -1L && !itemId.isNullOrBlank()) {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val noteDao = NoteDatabase.getInstance(context).noteDao()
                                val cached = NotesViewModel.latestNotesCache[noteId]
                                val dbNote = noteDao.getNoteByIdSync(noteId)
                                val note = when {
                                    cached != null && dbNote != null -> if (cached.updatedAt >= dbNote.updatedAt) cached else dbNote
                                    dbNote != null -> dbNote
                                    cached != null -> cached
                                    else -> null
                                }
                                if (note != null && note.isChecklist && !note.isArchived) {
                                    val items = note.getChecklistItems().toMutableList()
                                    val idx = items.indexOfFirst { it.id == itemId }
                                    if (idx != -1) {
                                        val current = items[idx]
                                        items[idx] = current.copy(isChecked = !current.isChecked)
                                        val (uncompleted, completed) = items.partition { !it.isChecked }
                                        val updatedNote = note.copy(
                                            checklistJson = ChecklistItem.listToJson(uncompleted + completed),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        noteDao.updateNote(updatedNote)
                                        NotesViewModel.latestNotesCache[updatedNote.id] = updatedNote
                                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                            NoteWidgetConfigHelper.setCurrentNoteId(context, appWidgetId, updatedNote.id)
                                        }

                                        AutoSyncManager.triggerDebouncedSync(context)
                                        NoteWidgetProvider.updateAllWidgets(context)
                                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                            NoteWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                                        }
                                    }
                                }
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }

            NoteWidgetProvider.ACTION_PREV_NOTE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val config = NoteWidgetConfigHelper.getConfig(context, appWidgetId)
                            val noteDao = NoteDatabase.getInstance(context).noteDao()
                            val rawNotes = if (config.filterMode == NoteWidgetFilterMode.SPECIFIC && config.specificNoteId != null) {
                                val single = noteDao.getNoteByIdSync(config.specificNoteId!!)
                                if (single != null && !single.isArchived && !single.isTrashed) listOf(single) else emptyList()
                            } else {
                                when (config.filterMode) {
                                    NoteWidgetFilterMode.NOTES -> noteDao.getTextNotesSync()
                                    NoteWidgetFilterMode.CHECKLISTS -> noteDao.getChecklistNotesSync()
                                    NoteWidgetFilterMode.PINNED -> noteDao.getPinnedNotesSync()
                                    else -> noteDao.getAllActiveNotesSync()
                                }
                            }
                            val sortedNotes = when (config.sortBy) {
                                NoteWidgetSortBy.RECENTLY_UPDATED -> rawNotes.sortedByDescending { it.updatedAt }
                                NoteWidgetSortBy.RECENTLY_CREATED -> rawNotes.sortedByDescending { it.createdAt }
                                NoteWidgetSortBy.PINNED_FIRST -> rawNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
                                NoteWidgetSortBy.ALPHABETICAL -> rawNotes.sortedBy { it.title.lowercase() }
                            }
                            if (sortedNotes.isNotEmpty()) {
                                val currentNoteId = NoteWidgetConfigHelper.getCurrentNoteId(context, appWidgetId)
                                val currentIdx = if (currentNoteId != null) {
                                    val fIdx = sortedNotes.indexOfFirst { it.id == currentNoteId }
                                    if (fIdx != -1) fIdx else NoteWidgetConfigHelper.getCurrentIndex(context, appWidgetId)
                                } else {
                                    NoteWidgetConfigHelper.getCurrentIndex(context, appWidgetId)
                                }
                                val newIdx = (currentIdx - 1 + sortedNotes.size) % sortedNotes.size
                                NoteWidgetConfigHelper.setCurrentIndex(context, appWidgetId, newIdx)
                                NoteWidgetConfigHelper.setCurrentNoteId(context, appWidgetId, sortedNotes[newIdx].id)
                            }
                            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_note_list_view)
                            NoteWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            NoteWidgetProvider.ACTION_NEXT_NOTE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val config = NoteWidgetConfigHelper.getConfig(context, appWidgetId)
                            val noteDao = NoteDatabase.getInstance(context).noteDao()
                            val rawNotes = if (config.filterMode == NoteWidgetFilterMode.SPECIFIC && config.specificNoteId != null) {
                                val single = noteDao.getNoteByIdSync(config.specificNoteId!!)
                                if (single != null && !single.isArchived && !single.isTrashed) listOf(single) else emptyList()
                            } else {
                                when (config.filterMode) {
                                    NoteWidgetFilterMode.NOTES -> noteDao.getTextNotesSync()
                                    NoteWidgetFilterMode.CHECKLISTS -> noteDao.getChecklistNotesSync()
                                    NoteWidgetFilterMode.PINNED -> noteDao.getPinnedNotesSync()
                                    else -> noteDao.getAllActiveNotesSync()
                                }
                            }
                            val sortedNotes = when (config.sortBy) {
                                NoteWidgetSortBy.RECENTLY_UPDATED -> rawNotes.sortedByDescending { it.updatedAt }
                                NoteWidgetSortBy.RECENTLY_CREATED -> rawNotes.sortedByDescending { it.createdAt }
                                NoteWidgetSortBy.PINNED_FIRST -> rawNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
                                NoteWidgetSortBy.ALPHABETICAL -> rawNotes.sortedBy { it.title.lowercase() }
                            }
                            if (sortedNotes.isNotEmpty()) {
                                val currentNoteId = NoteWidgetConfigHelper.getCurrentNoteId(context, appWidgetId)
                                val currentIdx = if (currentNoteId != null) {
                                    val fIdx = sortedNotes.indexOfFirst { it.id == currentNoteId }
                                    if (fIdx != -1) fIdx else NoteWidgetConfigHelper.getCurrentIndex(context, appWidgetId)
                                } else {
                                    NoteWidgetConfigHelper.getCurrentIndex(context, appWidgetId)
                                }
                                val newIdx = (currentIdx + 1) % sortedNotes.size
                                NoteWidgetConfigHelper.setCurrentIndex(context, appWidgetId, newIdx)
                                NoteWidgetConfigHelper.setCurrentNoteId(context, appWidgetId, sortedNotes[newIdx].id)
                            }
                            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_note_list_view)
                            NoteWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            NoteWidgetProvider.ACTION_CYCLE_FILTER -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val config = NoteWidgetConfigHelper.getConfig(context, appWidgetId)
                            val nextMode = config.filterMode.next()
                            NoteWidgetConfigHelper.setFilterMode(context, appWidgetId, nextMode)
                            NoteWidgetConfigHelper.setCurrentIndex(context, appWidgetId, 0)
                            NoteWidgetConfigHelper.setCurrentNoteId(context, appWidgetId, null)
                            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_note_list_view)
                            NoteWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
