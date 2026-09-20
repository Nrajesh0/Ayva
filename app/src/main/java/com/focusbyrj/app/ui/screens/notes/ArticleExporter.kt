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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.focusbyrj.app.data.note.ChecklistItem
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(
    val displayName: String,
    val fileExtension: String,
    val mimeType: String,
    val isBinary: Boolean
) {
    PDF("PDF Document (.pdf)", "pdf", "application/pdf", true),
    MARKDOWN("Markdown (.md)", "md", "text/markdown", false),
    MARKDOWN_FRONTMATTER("Markdown + Frontmatter (.md)", "md", "text/markdown", false),
    DOCX("Word Document (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true),
    HTML("Webpage (.html)", "html", "text/html", false),
    PLAIN_TEXT("Plain Text (.txt)", "txt", "text/plain", false)
}

object ArticleExporter {

    fun exportToMarkdown(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) {
            sb.append("# ").append(title).append("\n\n")
        }

        if (isChecklist && checklistItems.isNotEmpty()) {
            checklistItems.forEach { item ->
                val check = if (item.isChecked) "[x]" else "[ ]"
                sb.append("- ").append(check).append(" ").append(item.text).append("\n")
            }
            return sb.toString().trim()
        }

        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            val text = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).text else fallbackContent
            val spans = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).spans else emptyList()
            sb.append(convertTextSpansToMarkdown(text, spans))
            return sb.toString().trim()
        }

        blocks.forEach { block ->
            when (block) {
                is NotesnookBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        sb.append(convertTextSpansToMarkdown(block.text, block.spans)).append("\n\n")
                    }
                }
                is NotesnookBlock.Table -> {
                    if (block.data.isNotEmpty()) {
                        block.data.forEachIndexed { rowIndex, row ->
                            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                            if (rowIndex == 0) {
                                sb.append("| ").append(row.map { "---" }.joinToString(" | ")).append(" |\n")
                            }
                        }
                        sb.append("\n")
                    }
                }
                is NotesnookBlock.Code -> {
                    sb.append("```").append(block.language.lowercase()).append("\n")
                    sb.append(block.code).append("\n")
                    sb.append("```\n\n")
                }
                is NotesnookBlock.MathFormula -> {
                    sb.append("$$\n").append(block.formula).append("\n$$\n\n")
                }
                is NotesnookBlock.Callout -> {
                    val alertTag = when (block.calloutType.lowercase()) {
                        "warning" -> "WARNING"
                        "important" -> "IMPORTANT"
                        "tip" -> "TIP"
                        "info" -> "NOTE"
                        else -> "NOTE"
                    }
                    sb.append("> [!").append(alertTag).append("]\n")
                    sb.append("> ").append(block.text.replace("\n", "\n> ")).append("\n\n")
                }
                is NotesnookBlock.Quote -> {
                    sb.append("> ").append(block.text.replace("\n", "\n> ")).append("\n\n")
                }
                is NotesnookBlock.HorizontalRule -> {
                    sb.append("---\n\n")
                }
                is NotesnookBlock.OutlineItem -> {
                    val indent = "  ".repeat(block.level)
                    val bullet = if (block.isNumbered) "1." else "-"
                    sb.append(indent).append(bullet).append(" ").append(block.text).append("\n")
                }
                is NotesnookBlock.Image -> {
                    sb.append("![").append(block.caption).append("](").append(block.uri).append(")\n\n")
                }
                else -> {}
            }
        }

        return sb.toString().trim()
    }

    fun exportToMarkdownWithFrontmatter(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        labels: List<String> = emptyList(),
        updatedAt: Long = System.currentTimeMillis(),
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val dateStr = try {
            dateFormat.format(Date(updatedAt))
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(updatedAt))
        }

        val frontmatter = buildString {
            append("---\n")
            append("title: \"").append(title.replace("\"", "\\\"").ifBlank { "Untitled Note" }).append("\"\n")
            append("date: ").append(dateStr).append("\n")
            append("lastmod: ").append(dateStr).append("\n")
            if (labels.isNotEmpty()) {
                append("tags:\n")
                labels.forEach { label ->
                    append("  - \"").append(label.replace("\"", "\\\"")).append("\"\n")
                }
            } else {
                append("tags: [\"notes\"]\n")
            }
            append("categories: [\"Focus Notes\"]\n")
            append("draft: false\n")
            append("---\n\n")
        }

        val mdBody = exportToMarkdown(
            title = "", // Skip extra H1 title since it's already in frontmatter
            blocks = blocks,
            fallbackContent = fallbackContent,
            isChecklist = isChecklist,
            checklistItems = checklistItems
        )

        return frontmatter + mdBody
    }

    private fun convertTextSpansToMarkdown(text: String, spans: List<RichSpan>): String {
        if (spans.isEmpty()) return text
        if (text.isBlank()) return ""

        val sortedSpans = spans.sortedWith(compareBy({ it.start }, { -it.end }))
        val lineHeaderSpan = sortedSpans.firstOrNull {
            it.type in setOf(
                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
            )
        }

        var prefix = ""
        if (lineHeaderSpan != null) {
            prefix = when (lineHeaderSpan.type) {
                RichSpanType.HEADING_1 -> "# "
                RichSpanType.HEADING_2 -> "## "
                RichSpanType.HEADING_3 -> "### "
                RichSpanType.HEADING_4 -> "#### "
                RichSpanType.HEADING_5 -> "##### "
                else -> "###### "
            }
        }

        val inlineSpans = sortedSpans.filterNot {
            it.type in setOf(
                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
            )
        }

        val result = StringBuilder()
        var lastIdx = 0

        inlineSpans.forEach { span ->
            val s = span.start.coerceIn(0, text.length)
            val e = span.end.coerceIn(s, text.length)
            if (s > lastIdx) {
                result.append(text.substring(lastIdx, s))
            }
            val slice = text.substring(s, e)
            val formattedSlice = when (span.type) {
                RichSpanType.BOLD -> "**$slice**"
                RichSpanType.ITALIC -> "*$slice*"
                RichSpanType.UNDERLINE -> "<u>$slice</u>"
                RichSpanType.STRIKETHROUGH -> "~~$slice~~"
                RichSpanType.HIGHLIGHT -> "<mark>$slice</mark>"
                RichSpanType.CODE -> "`$slice`"
                RichSpanType.SUBSCRIPT -> "<sub>$slice</sub>"
                RichSpanType.SUPERSCRIPT -> "<sup>$slice</sup>"
                RichSpanType.LINK -> "[$slice](${span.payload ?: ""})"
                RichSpanType.QUOTE -> "> $slice"
                else -> slice
            }
            result.append(formattedSlice)
            lastIdx = e
        }

        if (lastIdx < text.length) {
            result.append(text.substring(lastIdx))
        }

        return prefix + result.toString()
    }

    fun exportToHtml(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("<title>").append(escapeHtml(title.ifBlank { "Focus Note" })).append("</title>\n")
        sb.append("<style>\n")
        sb.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.65; color: #1a1a1a; max-width: 740px; margin: 0 auto; padding: 32px 20px; }\n")
        sb.append("  h1, h2, h3, h4, h5, h6 { font-weight: 700; line-height: 1.25; margin-top: 1.5em; margin-bottom: 0.5em; color: #0f172a; }\n")
        sb.append("  h1 { font-size: 2.2em; border-bottom: 1px solid #eaeaea; padding-bottom: 0.3em; }\n")
        sb.append("  h2 { font-size: 1.6em; }\n")
        sb.append("  p { margin-bottom: 1.2em; color: #334155; }\n")
        sb.append("  blockquote { border-left: 4px solid #22c55e; margin: 1.2em 0; padding-left: 1rem; color: #475569; font-style: italic; background: #f8fafc; padding: 12px 16px; border-radius: 0 8px 8px 0; }\n")
        sb.append("  pre { background: #1e293b; color: #f1f5f9; padding: 14px 18px; border-radius: 8px; overflow-x: auto; font-family: monospace; font-size: 0.9em; }\n")
        sb.append("  code { background: #f1f5f9; color: #0f172a; padding: 2px 6px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }\n")
        sb.append("  table { width: 100%; border-collapse: collapse; margin: 1.5em 0; }\n")
        sb.append("  th, td { border: 1px solid #e2e8f0; padding: 10px 14px; text-align: left; }\n")
        sb.append("  th { background-color: #f8fafc; font-weight: 600; color: #0f172a; }\n")
        sb.append("  .callout { padding: 14px 18px; border-radius: 8px; margin: 1.2em 0; border-left: 4px solid #3b82f6; background-color: #eff6ff; }\n")
        sb.append("  .checklist { list-style: none; padding-left: 0; }\n")
        sb.append("  .checklist li { margin-bottom: 8px; display: flex; align-items: center; }\n")
        sb.append("  .checklist input[type='checkbox'] { margin-right: 10px; width: 16px; height: 16px; accent-color: #22c55e; }\n")
        sb.append("  hr { border: none; border-top: 1px solid #e2e8f0; margin: 2em 0; }\n")
        sb.append("</style>\n</head>\n<body>\n<article>\n")

        if (title.isNotBlank()) {
            sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n")
        }

        if (isChecklist && checklistItems.isNotEmpty()) {
            sb.append("<ul class=\"checklist\">\n")
            checklistItems.forEach { item ->
                val checked = if (item.isChecked) " checked" else ""
                val strikeStart = if (item.isChecked) "<del>" else ""
                val strikeEnd = if (item.isChecked) "</del>" else ""
                sb.append("  <li><input type=\"checkbox\"").append(checked).append(" disabled> ")
                    .append(strikeStart).append(escapeHtml(item.text)).append(strikeEnd).append("</li>\n")
            }
            sb.append("</ul>\n")
        } else if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            val text = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).text else fallbackContent
            val spans = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).spans else emptyList()
            sb.append(convertTextSpansToHtml(text, spans))
        } else {
            blocks.forEach { block ->
                when (block) {
                    is NotesnookBlock.Text -> {
                        if (block.text.isNotBlank()) {
                            sb.append(convertTextSpansToHtml(block.text, block.spans))
                        }
                    }
                    is NotesnookBlock.Table -> {
                        if (block.data.isNotEmpty()) {
                            sb.append("<table>\n")
                            block.data.forEachIndexed { rowIndex, row ->
                                val tag = if (rowIndex == 0) "th" else "td"
                                sb.append("  <tr>\n")
                                row.forEach { cell ->
                                    sb.append("    <").append(tag).append(">").append(escapeHtml(cell)).append("</").append(tag).append(">\n")
                                }
                                sb.append("  </tr>\n")
                            }
                            sb.append("</table>\n")
                        }
                    }
                    is NotesnookBlock.Code -> {
                        sb.append("<pre><code class=\"language-").append(escapeHtml(block.language.lowercase())).append("\">")
                            .append(escapeHtml(block.code)).append("</code></pre>\n")
                    }
                    is NotesnookBlock.MathFormula -> {
                        sb.append("<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 18px; margin: 1.2em 0; text-align: center;\">\n")
                        sb.append("  <span style=\"display: inline-block; font-size: 0.8em; font-weight: 600; color: #16a34a; background: #dcfce7; padding: 2px 8px; border-radius: 4px; margin-bottom: 8px;\">&sum; KaTeX Math</span><br>\n")
                        sb.append("  <span style=\"font-family: 'Cambria Math', Georgia, serif; font-size: 1.25em; font-style: italic; color: #0f172a;\">")
                            .append(escapeHtml(block.formula.ifBlank { "E = mc²" })).append("</span>\n</div>\n")
                    }
                    is NotesnookBlock.Embed -> {
                        sb.append("<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 16px; margin: 1.2em 0;\">\n")
                        sb.append("  <strong>Media: </strong><a href=\"").append(escapeHtml(block.url)).append("\" target=\"_blank\">")
                            .append(escapeHtml(block.title.ifBlank { block.url })).append("</a>\n</div>\n")
                    }
                    is NotesnookBlock.Attachment -> {
                        sb.append("<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 16px; margin: 1.2em 0;\">\n")
                        sb.append("  <strong>&#128206; Attachment: </strong>").append(escapeHtml(block.fileName)).append(" (").append(escapeHtml(block.fileSize)).append(")\n</div>\n")
                    }
                    is NotesnookBlock.Callout -> {
                        sb.append("<div class=\"callout\">\n  <p>").append(escapeHtml(block.text)).append("</p>\n</div>\n")
                    }
                    is NotesnookBlock.Quote -> {
                        sb.append("<blockquote><p>").append(escapeHtml(block.text)).append("</p></blockquote>\n")
                    }
                    is NotesnookBlock.HorizontalRule -> {
                        sb.append("<hr>\n")
                    }
                    is NotesnookBlock.OutlineItem -> {
                        val bullet = if (block.isNumbered) "1. " else "• "
                        val margin = (block.level + 1) * 20
                        sb.append("<p style=\"margin-left: ").append(margin).append("px;\">").append(bullet).append(escapeHtml(block.text)).append("</p>\n")
                    }
                    else -> {}
                }
            }
        }

        sb.append("</article>\n</body>\n</html>")
        return sb.toString()
    }

    private fun convertTextSpansToHtml(text: String, spans: List<RichSpan>): String {
        if (text.isBlank()) return ""
        val sortedSpans = spans.sortedWith(compareBy({ it.start }, { -it.end }))
        val lineHeaderSpan = sortedSpans.firstOrNull {
            it.type in setOf(
                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
            )
        }

        val tag = when (lineHeaderSpan?.type) {
            RichSpanType.HEADING_1 -> "h1"
            RichSpanType.HEADING_2 -> "h2"
            RichSpanType.HEADING_3 -> "h3"
            RichSpanType.HEADING_4 -> "h4"
            RichSpanType.HEADING_5 -> "h5"
            RichSpanType.HEADING_6 -> "h6"
            else -> "p"
        }

        val inlineSpans = sortedSpans.filterNot {
            it.type in setOf(
                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
            )
        }

        val result = StringBuilder()
        var lastIdx = 0

        inlineSpans.forEach { span ->
            val s = span.start.coerceIn(0, text.length)
            val e = span.end.coerceIn(s, text.length)
            if (s > lastIdx) {
                result.append(escapeHtml(text.substring(lastIdx, s)))
            }
            val slice = escapeHtml(text.substring(s, e))
            val formattedSlice = when (span.type) {
                RichSpanType.BOLD -> "<b>$slice</b>"
                RichSpanType.ITALIC -> "<i>$slice</i>"
                RichSpanType.UNDERLINE -> "<u>$slice</u>"
                RichSpanType.STRIKETHROUGH -> "<s>$slice</s>"
                RichSpanType.HIGHLIGHT -> "<mark>$slice</mark>"
                RichSpanType.CODE -> "<code>$slice</code>"
                RichSpanType.SUBSCRIPT -> "<sub>$slice</sub>"
                RichSpanType.SUPERSCRIPT -> "<sup>$slice</sup>"
                RichSpanType.LINK -> "<a href=\"${escapeHtml(span.payload ?: "")}\">$slice</a>"
                RichSpanType.QUOTE -> "<blockquote>$slice</blockquote>"
                else -> slice
            }
            result.append(formattedSlice)
            lastIdx = e
        }

        if (lastIdx < text.length) {
            result.append(escapeHtml(text.substring(lastIdx)))
        }

        return "<$tag>${result.toString()}</$tag>\n"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    fun generateExportBytes(
        format: ExportFormat,
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        labels: List<String> = emptyList(),
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): ByteArray {
        return when (format) {
            ExportFormat.PDF -> {
                ArticlePdfGenerator.generatePdf(
                    title = title,
                    blocks = blocks,
                    fallbackContent = fallbackContent,
                    isChecklist = isChecklist,
                    checklistItems = checklistItems
                )
            }
            ExportFormat.DOCX -> {
                ArticleDocxGenerator.generateDocx(
                    title = title,
                    blocks = blocks,
                    fallbackContent = fallbackContent,
                    isChecklist = isChecklist,
                    checklistItems = checklistItems
                )
            }
            ExportFormat.MARKDOWN -> {
                exportToMarkdown(
                    title = title,
                    blocks = blocks,
                    fallbackContent = fallbackContent,
                    isChecklist = isChecklist,
                    checklistItems = checklistItems
                ).toByteArray(StandardCharsets.UTF_8)
            }
            ExportFormat.MARKDOWN_FRONTMATTER -> {
                exportToMarkdownWithFrontmatter(
                    title = title,
                    blocks = blocks,
                    fallbackContent = fallbackContent,
                    labels = labels,
                    isChecklist = isChecklist,
                    checklistItems = checklistItems
                ).toByteArray(StandardCharsets.UTF_8)
            }
            ExportFormat.HTML -> {
                exportToHtml(
                    title = title,
                    blocks = blocks,
                    fallbackContent = fallbackContent,
                    isChecklist = isChecklist,
                    checklistItems = checklistItems
                ).toByteArray(StandardCharsets.UTF_8)
            }
            ExportFormat.PLAIN_TEXT -> {
                val text = buildString {
                    if (title.isNotBlank()) append(title).append("\n\n")
                    if (isChecklist && checklistItems.isNotEmpty()) {
                        checklistItems.forEach { append(if (it.isChecked) "[x] " else "[ ] ").append(it.text).append("\n") }
                    } else {
                        append(fallbackContent)
                    }
                }.trim()
                text.toByteArray(StandardCharsets.UTF_8)
            }
        }
    }

    private fun sanitizeFileName(title: String, extension: String): String {
        val safeTitle = title.trim().ifBlank { "Focus_Note" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "${safeTitle}_$timestamp.$extension"
    }

    fun shareExportedFile(
        context: Context,
        format: ExportFormat,
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        labels: List<String> = emptyList(),
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ) {
        try {
            val bytes = generateExportBytes(
                format = format,
                title = title,
                blocks = blocks,
                fallbackContent = fallbackContent,
                labels = labels,
                isChecklist = isChecklist,
                checklistItems = checklistItems
            )

            val fileName = sanitizeFileName(title, format.fileExtension)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Exported Note" })
                if (!format.isBinary) {
                    putExtra(Intent.EXTRA_TEXT, String(bytes, StandardCharsets.UTF_8))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ${format.displayName}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun saveToDownloads(
        context: Context,
        format: ExportFormat,
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        labels: List<String> = emptyList(),
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): Boolean {
        return try {
            val bytes = generateExportBytes(
                format = format,
                title = title,
                blocks = blocks,
                fallbackContent = fallbackContent,
                labels = labels,
                isChecklist = isChecklist,
                checklistItems = checklistItems
            )
            val fileName = sanitizeFileName(title, format.fileExtension)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FocusNotes")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    Toast.makeText(context, "Saved to Downloads/FocusNotes/$fileName", Toast.LENGTH_LONG).show()
                    true
                } else {
                    false
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "FocusNotes").apply { mkdirs() }
                val targetFile = File(appDir, fileName)
                FileOutputStream(targetFile).use { it.write(bytes) }
                Toast.makeText(context, "Saved to Downloads/FocusNotes/$fileName", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
