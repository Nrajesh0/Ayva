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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.focusbyrj.app.R
import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import kotlinx.coroutines.runBlocking

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteWidgetRemoteViewsFactory(applicationContext, intent)
    }
}

class NoteWidgetRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = run {
        val idFromExtra = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (idFromExtra != AppWidgetManager.INVALID_APPWIDGET_ID && idFromExtra > 0) {
            idFromExtra
        } else {
            intent.data?.host?.toIntOrNull()
                ?: intent.data?.pathSegments?.firstOrNull()?.toIntOrNull()
                ?: run {
                    val mgr = AppWidgetManager.getInstance(context)
                    val ids = mgr.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
                    ids?.firstOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
                }
        }
    }

    private var currentNote: NoteEntity? = null
    private var checklistItems: List<ChecklistItem> = emptyList()
    private var textParagraphs: List<String> = emptyList()
    private var widgetConfig: NoteWidgetConfig = NoteWidgetConfig()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            widgetConfig = NoteWidgetConfigHelper.getConfig(context, appWidgetId)
            val noteDao = NoteDatabase.getInstance(context).noteDao()

            val targetNote = runBlocking {
                if (widgetConfig.filterMode == NoteWidgetFilterMode.SPECIFIC && widgetConfig.specificNoteId != null) {
                    val specific = noteDao.getNoteByIdSync(widgetConfig.specificNoteId!!)
                    if (specific != null && !specific.isArchived && !specific.isTrashed) {
                        specific
                    } else {
                        // Fallback to active notes if specific note was deleted, trashed or archived
                        val rawNotes = noteDao.getAllActiveNotesSync()
                        if (rawNotes.isNotEmpty()) rawNotes.first() else null
                    }
                } else {
                    val rawNotes = when (widgetConfig.filterMode) {
                        NoteWidgetFilterMode.NOTES -> noteDao.getTextNotesSync()
                        NoteWidgetFilterMode.CHECKLISTS -> noteDao.getChecklistNotesSync()
                        NoteWidgetFilterMode.PINNED -> noteDao.getPinnedNotesSync()
                        else -> noteDao.getAllActiveNotesSync()
                    }

                    // Apply the exact same sort order as NoteWidgetProvider to guarantee header/content sync
                    val sortedNotes = when (widgetConfig.sortBy) {
                        NoteWidgetSortBy.RECENTLY_UPDATED -> rawNotes.sortedByDescending { it.updatedAt }
                        NoteWidgetSortBy.RECENTLY_CREATED -> rawNotes.sortedByDescending { it.createdAt }
                        NoteWidgetSortBy.PINNED_FIRST -> rawNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
                        NoteWidgetSortBy.ALPHABETICAL -> rawNotes.sortedBy { it.title.lowercase() }
                    }

                    if (sortedNotes.isNotEmpty()) {
                        val currentNoteId = NoteWidgetConfigHelper.getCurrentNoteId(context, appWidgetId)
                        val noteById = if (currentNoteId != null) sortedNotes.find { it.id == currentNoteId } else null
                        if (noteById != null) {
                            noteById
                        } else {
                            val index = NoteWidgetConfigHelper.getCurrentIndex(context, appWidgetId).coerceAtLeast(0)
                            val safeIndex = index % sortedNotes.size
                            sortedNotes[safeIndex]
                        }
                    } else {
                        null
                    }
                }
            }

            val cached = if (targetNote != null) com.focusbyrj.app.ui.screens.notes.NotesViewModel.latestNotesCache[targetNote.id] else null
            val resolvedNote = if (cached != null && cached.updatedAt >= (targetNote?.updatedAt ?: 0L)) cached else targetNote

            currentNote = resolvedNote
            if (resolvedNote != null && resolvedNote.isChecklist) {
                val rawItems = resolvedNote.getChecklistItems()
                val (uncompleted, completed) = rawItems.partition { !it.isChecked }
                val sorted = uncompleted + completed
                checklistItems = if (sorted.isEmpty()) {
                    listOf(ChecklistItem(id = "", text = "(Empty checklist - tap to add items)", isChecked = false))
                } else {
                    sorted
                }
                textParagraphs = emptyList()
            } else if (resolvedNote != null) {
                checklistItems = emptyList()
                val rawText = resolvedNote.content.ifBlank { "(Empty note - tap to write)" }
                textParagraphs = rawText.split("\n")
            } else {
                checklistItems = emptyList()
                textParagraphs = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            checklistItems = emptyList()
            textParagraphs = emptyList()
            currentNote = null
        }
    }

    override fun onDestroy() {
        checklistItems = emptyList()
        textParagraphs = emptyList()
        currentNote = null
    }

    override fun getCount(): Int {
        val note = currentNote ?: return 0
        return if (note.isChecklist) checklistItems.size else textParagraphs.size
    }

    override fun getViewAt(position: Int): RemoteViews? {
        val note = currentNote ?: return null

        val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = if (widgetConfig.matchNoteColor && !note.colorKey.isNullOrEmpty() && note.colorKey != "default") isSystemDark else widgetConfig.theme.isDark

        val bgColors = NoteWidgetDrawableGenerator.getWidgetColors(
            context = context,
            config = widgetConfig,
            noteColorKey = note.colorKey,
            isSystemDark = isSystemDark
        )

        if (note.isChecklist) {
            if (position < 0 || position >= checklistItems.size) return null
            val item = checklistItems[position]

            val views = RemoteViews(context.packageName, R.layout.widget_note_item)

            // Drag Handle color tinting
            views.setInt(R.id.widget_note_item_drag_handle, "setColorFilter", bgColors.secondaryTextColor)

            // Remove X button color tinting
            views.setInt(R.id.widget_note_item_remove, "setColorFilter", bgColors.secondaryTextColor)

            // Render Checkbox
            val checkBitmap = NoteWidgetDrawableGenerator.createCheckboxBitmap(
                isChecked = item.isChecked,
                accentColorInt = bgColors.accentColor,
                secondaryTextColorInt = bgColors.secondaryTextColor,
                isDark = isDark
            )
            views.setImageViewBitmap(R.id.widget_note_item_checkbox, checkBitmap)

            // Render Text & Strike-through
            views.setTextViewText(R.id.widget_note_item_text, item.text)
            views.setTextViewTextSize(R.id.widget_note_item_text, TypedValue.COMPLEX_UNIT_SP, widgetConfig.textSize.spValue)
            if (item.isChecked) {
                views.setTextColor(R.id.widget_note_item_text, bgColors.secondaryTextColor)
                views.setInt(
                    R.id.widget_note_item_text,
                    "setPaintFlags",
                    Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
                )
            } else {
                views.setTextColor(R.id.widget_note_item_text, bgColors.primaryTextColor)
                views.setInt(
                    R.id.widget_note_item_text,
                    "setPaintFlags",
                    Paint.ANTI_ALIAS_FLAG
                )
            }

            if (item.id.isEmpty()) {
                // Placeholder item for empty checklist
                views.setViewVisibility(R.id.widget_note_item_remove, View.GONE)
                views.setViewVisibility(R.id.widget_note_item_drag_handle, View.GONE)
                val editIntent = Intent().apply {
                    putExtra(NoteWidgetProvider.EXTRA_ACTION_TYPE, NoteWidgetProvider.ACTION_TYPE_EDIT)
                    putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, note.id)
                }
                views.setOnClickFillInIntent(R.id.widget_note_item_checkbox, editIntent)
                views.setOnClickFillInIntent(R.id.widget_note_item_text, editIntent)
            } else {
                views.setViewVisibility(R.id.widget_note_item_remove, View.VISIBLE)
                views.setViewVisibility(R.id.widget_note_item_drag_handle, View.VISIBLE)

                // Checkbox click intent (toggles item state)
                val toggleIntent = Intent().apply {
                    putExtra(NoteWidgetProvider.EXTRA_ACTION_TYPE, NoteWidgetProvider.ACTION_TYPE_TOGGLE)
                    putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, note.id)
                    putExtra(NoteWidgetProvider.EXTRA_ITEM_ID, item.id)
                }
                views.setOnClickFillInIntent(R.id.widget_note_item_checkbox, toggleIntent)

                // Remove X click intent (deletes item directly from widget)
                val removeIntent = Intent().apply {
                    putExtra(NoteWidgetProvider.EXTRA_ACTION_TYPE, NoteWidgetProvider.ACTION_TYPE_REMOVE)
                    putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, note.id)
                    putExtra(NoteWidgetProvider.EXTRA_ITEM_ID, item.id)
                }
                views.setOnClickFillInIntent(R.id.widget_note_item_remove, removeIntent)

                // Drag handle & root text click intent (opens editor / reorder view)
                val editIntent = Intent().apply {
                    putExtra(NoteWidgetProvider.EXTRA_ACTION_TYPE, NoteWidgetProvider.ACTION_TYPE_EDIT)
                    putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, note.id)
                    putExtra(NoteWidgetProvider.EXTRA_ITEM_ID, item.id)
                }
                views.setOnClickFillInIntent(R.id.widget_note_item_drag_handle, editIntent)
                views.setOnClickFillInIntent(R.id.widget_note_item_text, editIntent)
            }

            return views
        } else {
            if (position < 0 || position >= textParagraphs.size) return null
            val paragraph = textParagraphs[position]

            val views = RemoteViews(context.packageName, R.layout.widget_note_text_item)

            // Text Body
            val displayText = if (paragraph.isEmpty()) " " else paragraph
            views.setTextViewText(R.id.widget_note_text_item_body, displayText)
            views.setTextColor(R.id.widget_note_text_item_body, bgColors.primaryTextColor)
            views.setTextViewTextSize(
                R.id.widget_note_text_item_body,
                TypedValue.COMPLEX_UNIT_SP,
                widgetConfig.textSize.spValue
            )

            // Top Image (only on position 0 if image exists)
            val imageUris = note.getImageUris()
            if (position == 0 && imageUris.isNotEmpty()) {
                val bmp = com.focusbyrj.app.data.note.NoteImageHelper.loadBitmapForWidget(context, imageUris.first())
                if (bmp != null) {
                    views.setViewVisibility(R.id.widget_note_text_item_image, android.view.View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_note_text_item_image, bmp)
                } else {
                    views.setViewVisibility(R.id.widget_note_text_item_image, android.view.View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_note_text_item_image, android.view.View.GONE)
            }

            // FillInIntent to edit note on click
            val editIntent = Intent().apply {
                putExtra(NoteWidgetProvider.EXTRA_ACTION_TYPE, NoteWidgetProvider.ACTION_TYPE_EDIT)
                putExtra(NoteWidgetProvider.EXTRA_NOTE_ID, note.id)
            }
            views.setOnClickFillInIntent(R.id.widget_note_text_item_root, editIntent)
            views.setOnClickFillInIntent(R.id.widget_note_text_item_body, editIntent)

            return views
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
