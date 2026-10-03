/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.notes

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ArticleExporterHelper {

    private val RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Resolves effective blocks for export.
     * If the blocks list is empty and fallbackContent contains serialized Notesnook blocks,
     * parses the blocks so that exporters (Markdown, DOCX, PDF) format structured blocks
     * rather than leaking raw JSON markup into the user's exported file.
     */
    fun getEffectiveBlocks(blocks: List<NotesnookBlock>, fallbackContent: String): List<NotesnookBlock> {
        return if (blocks.isEmpty() && fallbackContent.contains(NotesnookBlockManager.BLOCKS_PREFIX)) {
            try {
                NotesnookBlockManager.parse(fallbackContent)
            } catch (_: Exception) {
                blocks
            }
        } else {
            blocks
        }
    }

    /**
     * Sanitizes export file names to prevent path traversal, hidden dot-files,
     * FAT32 / Android MediaStore issues, and reserved filenames.
     */
    fun sanitizeFileName(title: String, extension: String, date: Date = Date()): String {
        var cleaned = title.trim()
            .replace("/", "_")
            .replace("\\", "_")
            .replace(Regex("""\.\.+"""), "_")
            .replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
            .trim('.', '_')

        if (cleaned.isBlank() || RESERVED_NAMES.contains(cleaned.uppercase(Locale.ROOT))) {
            cleaned = "Focus_Note"
        } else {
            cleaned = cleaned.take(50)
        }

        val safeExt = extension.trim().trimStart('.').filter { it.isLetterOrDigit() }.ifBlank { "txt" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(date)
        return "${cleaned}_$timestamp.$safeExt"
    }
}
