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

package com.focusbyrj.app.ui.screens.notes

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.focusbyrj.app.data.note.ChecklistItem
import java.util.UUID

data class ParsedSharedNote(
    val title: String,
    val content: String,
    val isChecklist: Boolean,
    val checklistItems: List<ChecklistItem>,
    val imageUris: List<Uri>
)

object KeepNoteShareParser {

    private val CHECKED_REGEX = Regex("""^(\s*[-*•]?\s*(☑|\[[xX]\]|\([xX]\))\s*)(.*)$""")
    private val UNCHECKED_REGEX = Regex("""^(\s*[-*•]?\s*(☐|\[\s*\]|\(\s*\))\s*)(.*)$""")
    private val BULLET_REGEX = Regex("""^(\s*[-*•]\s+)(.*)$""")

    fun parseIntent(intent: Intent): ParsedSharedNote {
        val rawSubject = (
            intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
        )?.trim()

        var rawText: String? = null

        // 1. Check EXTRA_TEXT (String or CharSequence)
        val extraText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        if (extraText != null && extraText.isNotBlank()) {
            rawText = extraText.toString().trim()
        }

        // 2. Check EXTRA_HTML_TEXT
        if (rawText.isNullOrBlank()) {
            val htmlText = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
            if (!htmlText.isNullOrBlank()) {
                rawText = android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }
        }

        // 3. Check ClipData item text or HTML
        if (rawText.isNullOrBlank()) {
            intent.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    val itemText = item.text?.toString() ?: item.htmlText?.let {
                        android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    }
                    if (!itemText.isNullOrBlank()) {
                        rawText = itemText.trim()
                        break
                    }
                }
            }
        }

        // 4. Check data string if it's not a content/file URI
        if (rawText.isNullOrBlank() && intent.data != null) {
            val scheme = intent.data?.scheme
            if (scheme != "content" && scheme != "file") {
                rawText = intent.dataString
            }
        }

        val imageUris = extractImageUris(intent)

        return parseContent(rawSubject, rawText, imageUris)
    }

    fun extractImageUris(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()

        // 1. Single stream
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { uris.add(it) }
        }

        // 2. Multiple streams
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        }

        // 3. ClipData
        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uri ->
                    if (!uris.contains(uri)) {
                        uris.add(uri)
                    }
                }
            }
        }

        return uris.distinct()
    }

    fun parseContent(rawSubject: String?, rawText: String?, imageUris: List<Uri> = emptyList()): ParsedSharedNote {
        val lines = rawText?.lines()?.map { it.trimEnd() } ?: emptyList()
        val nonEmptyLines = lines.filter { it.isNotBlank() }

        var finalTitle = rawSubject.orEmpty().trim()
        var remainingLines = lines

        // If an explicit subject was provided:
        // Google Keep often sets EXTRA_SUBJECT = Title and EXTRA_TEXT = "Title\n\nBody" OR EXTRA_TEXT = "Body"
        if (finalTitle.isNotBlank()) {
            if (nonEmptyLines.isNotEmpty() && nonEmptyLines.first().trim() == finalTitle) {
                // The first non-empty line duplicates the subject/title, remove it from body
                var removed = false
                remainingLines = lines.filter { line ->
                    if (!removed && line.trim() == finalTitle) {
                        removed = true
                        false
                    } else {
                        true
                    }
                }
            }
        } else if (nonEmptyLines.isNotEmpty()) {
            // No explicit subject provided: extract title from first line if it looks like a heading or if there are multiple lines
            val firstLine = nonEmptyLines.first().trim()
            val isFirstLineChecklist = isChecklistLine(firstLine)

            if (!isFirstLineChecklist && nonEmptyLines.size > 1 && firstLine.length <= 100) {
                finalTitle = firstLine
                // Remove the first non-empty line
                var removed = false
                remainingLines = lines.filter { line ->
                    if (!removed && line.trim() == firstLine) {
                        removed = true
                        false
                    } else {
                        true
                    }
                }
            } else if (!isFirstLineChecklist && nonEmptyLines.size == 1) {
                // Only 1 single line shared. If it's short and images exist, treat as title; otherwise treat as note content
                if (firstLine.length <= 60 && imageUris.isNotEmpty()) {
                    finalTitle = firstLine
                    remainingLines = emptyList()
                } else {
                    // Let it be the body content
                    finalTitle = ""
                    remainingLines = lines
                }
            }
        }

        // Check if remaining lines form a checklist
        val checklistCount = remainingLines.count { isChecklistLine(it) }
        val hasCheckboxes = checklistCount > 0

        if (hasCheckboxes) {
            val items = mutableListOf<ChecklistItem>()
            remainingLines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank()) {
                    val (isChecked, cleanText) = parseChecklistLine(trimmed)
                    items.add(ChecklistItem(id = UUID.randomUUID().toString(), text = cleanText, isChecked = isChecked))
                }
            }
            return ParsedSharedNote(
                title = finalTitle,
                content = "",
                isChecklist = true,
                checklistItems = items,
                imageUris = imageUris
            )
        } else {
            val body = remainingLines.joinToString("\n").trim()
            // If both title and body are blank, but images exist
            val resolvedTitle = if (finalTitle.isBlank() && body.isBlank() && imageUris.isNotEmpty()) {
                "Imported Image Note"
            } else {
                finalTitle
            }

            return ParsedSharedNote(
                title = resolvedTitle,
                content = body,
                isChecklist = false,
                checklistItems = emptyList(),
                imageUris = imageUris
            )
        }
    }

    private fun isChecklistLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return false
        return CHECKED_REGEX.matches(trimmed) || UNCHECKED_REGEX.matches(trimmed)
    }

    private fun parseChecklistLine(line: String): Pair<Boolean, String> {
        val checkedMatch = CHECKED_REGEX.matchEntire(line)
        if (checkedMatch != null) {
            val itemText = checkedMatch.groupValues[3].trim()
            return true to itemText
        }

        val uncheckedMatch = UNCHECKED_REGEX.matchEntire(line)
        if (uncheckedMatch != null) {
            val itemText = uncheckedMatch.groupValues[3].trim()
            return false to itemText
        }

        val bulletMatch = BULLET_REGEX.matchEntire(line)
        if (bulletMatch != null) {
            val itemText = bulletMatch.groupValues[2].trim()
            return false to itemText
        }

        return false to line.trim()
    }
}
