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

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Authentic Notesnook first-class block models.
 * Every note element (Text, Table, Horizontal Rule, Code Block, Math Formula,
 * Callout, Quote, Outline List, Embed, Attachment, Image) is a native, seamless widget.
 */
sealed interface NotesnookBlock {
    val id: String

    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        var text: String = "",
        var spans: List<RichSpan> = emptyList()
    ) : NotesnookBlock

    data class Table(
        override val id: String = UUID.randomUUID().toString(),
        var rows: Int = 2,
        var cols: Int = 2,
        val data: MutableList<MutableList<String>> = mutableListOf()
    ) : NotesnookBlock

    data class HorizontalRule(
        override val id: String = UUID.randomUUID().toString()
    ) : NotesnookBlock

    data class Code(
        override val id: String = UUID.randomUUID().toString(),
        var language: String = "Kotlin",
        var code: String = ""
    ) : NotesnookBlock

    data class MathFormula(
        override val id: String = UUID.randomUUID().toString(),
        var formula: String = "E = mc²",
        var isInline: Boolean = false
    ) : NotesnookBlock

    data class Callout(
        override val id: String = UUID.randomUUID().toString(),
        var calloutType: String = "note", // note, warning, important, tip, info, success
        var text: String = ""
    ) : NotesnookBlock

    data class Quote(
        override val id: String = UUID.randomUUID().toString(),
        var text: String = ""
    ) : NotesnookBlock

    data class OutlineItem(
        override val id: String = UUID.randomUUID().toString(),
        var level: Int = 0, // 0 = root, 1 = sub, 2 = sub-sub
        var text: String = "",
        var isCollapsed: Boolean = false,
        var isNumbered: Boolean = true
    ) : NotesnookBlock

    data class Embed(
        override val id: String = UUID.randomUUID().toString(),
        var type: String = "YouTube", // YouTube, Audio, Web Link, Figma, CodePen
        var url: String = "",
        var title: String = ""
    ) : NotesnookBlock

    data class Attachment(
        override val id: String = UUID.randomUUID().toString(),
        var uri: String = "",
        var fileName: String = "attachment.pdf",
        var fileSize: String = "1.2 MB"
    ) : NotesnookBlock

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        var uri: String = "",
        var caption: String = ""
    ) : NotesnookBlock
}

object NotesnookBlockManager {
    const val BLOCKS_PREFIX = "<!--NOTESNOOK_BLOCKS:"
    const val BLOCKS_SUFFIX = ":BLOCKS_END-->"
    private const val TABLE_START = "<!--TABLE_START:"
    private const val TABLE_END = ":TABLE_END-->"

    /**
     * Serializes a list of NotesnookBlocks into a persistent string.
     */
    fun serialize(blocks: List<NotesnookBlock>): String {
        if (blocks.isEmpty()) return ""

        // If the note consists of only a single Text block with no special spans,
        // serialize via standard RichTextEngine to keep maximum backward compatibility
        if (blocks.size == 1 && blocks[0] is NotesnookBlock.Text) {
            val textBlock = blocks[0] as NotesnookBlock.Text
            if (textBlock.spans.isEmpty()) {
                return textBlock.text
            }
        }

        try {
            val root = JSONObject()
            root.put("version", 1)
            val array = JSONArray()

            for (block in blocks) {
                val obj = JSONObject()
                obj.put("id", block.id)
                when (block) {
                    is NotesnookBlock.Text -> {
                        obj.put("type", "text")
                        obj.put("text", block.text)
                        val spansArr = JSONArray()
                        for (s in block.spans) {
                            val spanObj = JSONObject()
                            spanObj.put("type", s.type.name)
                            spanObj.put("start", s.start)
                            spanObj.put("end", s.end)
                            s.payload?.let { spanObj.put("payload", it) }
                            spansArr.put(spanObj)
                        }
                        obj.put("spans", spansArr)
                    }
                    is NotesnookBlock.Table -> {
                        obj.put("type", "table")
                        obj.put("rows", block.rows)
                        obj.put("cols", block.cols)
                        val dataArr = JSONArray()
                        for (r in 0 until block.rows) {
                            val rowArr = JSONArray()
                            for (c in 0 until block.cols) {
                                rowArr.put(block.data.getOrNull(r)?.getOrNull(c) ?: "")
                            }
                            dataArr.put(rowArr)
                        }
                        obj.put("data", dataArr)
                    }
                    is NotesnookBlock.HorizontalRule -> {
                        obj.put("type", "horizontal_rule")
                    }
                    is NotesnookBlock.Code -> {
                        obj.put("type", "code")
                        obj.put("language", block.language)
                        obj.put("code", block.code)
                    }
                    is NotesnookBlock.MathFormula -> {
                        obj.put("type", "math")
                        obj.put("formula", block.formula)
                        obj.put("isInline", block.isInline)
                    }
                    is NotesnookBlock.Callout -> {
                        obj.put("type", "callout")
                        obj.put("calloutType", block.calloutType)
                        obj.put("text", block.text)
                    }
                    is NotesnookBlock.Quote -> {
                        obj.put("type", "quote")
                        obj.put("text", block.text)
                    }
                    is NotesnookBlock.OutlineItem -> {
                        obj.put("type", "outline")
                        obj.put("level", block.level)
                        obj.put("text", block.text)
                        obj.put("isCollapsed", block.isCollapsed)
                        obj.put("isNumbered", block.isNumbered)
                    }
                    is NotesnookBlock.Embed -> {
                        obj.put("type", "embed")
                        obj.put("embedType", block.type)
                        obj.put("url", block.url)
                        obj.put("title", block.title)
                    }
                    is NotesnookBlock.Attachment -> {
                        obj.put("type", "attachment")
                        obj.put("uri", block.uri)
                        obj.put("fileName", block.fileName)
                        obj.put("fileSize", block.fileSize)
                    }
                    is NotesnookBlock.Image -> {
                        obj.put("type", "image")
                        obj.put("uri", block.uri)
                        obj.put("caption", block.caption)
                    }
                }
                array.put(obj)
            }
            root.put("blocks", array)
            return "$BLOCKS_PREFIX${root}$BLOCKS_SUFFIX"
        } catch (_: Exception) {
            // Fallback plain string
            return toPlainText(blocks)
        }
    }

    /**
     * Parses stored content string into a list of NotesnookBlocks.
     * Seamlessly handles blocks JSON, legacy table tags, markdown, and plain text.
     */
    fun parse(raw: String): List<NotesnookBlock> {
        if (raw.isBlank()) {
            return listOf(NotesnookBlock.Text())
        }

        // 1. Check for native Notesnook Blocks JSON
        if (raw.contains(BLOCKS_PREFIX) && raw.contains(BLOCKS_SUFFIX)) {
            try {
                val start = raw.indexOf(BLOCKS_PREFIX) + BLOCKS_PREFIX.length
                val end = raw.indexOf(BLOCKS_SUFFIX, startIndex = start)
                if (start in 0 until end) {
                    val jsonStr = raw.substring(start, end)
                    val root = JSONObject(jsonStr)
                    val array = root.optJSONArray("blocks")
                    if (array != null && array.length() > 0) {
                        val blocks = mutableListOf<NotesnookBlock>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val id = obj.optString("id", UUID.randomUUID().toString())
                            when (obj.optString("type")) {
                                "text" -> {
                                    val text = obj.optString("text", "")
                                    val spans = mutableListOf<RichSpan>()
                                    val spansArr = obj.optJSONArray("spans")
                                    if (spansArr != null) {
                                        for (s in 0 until spansArr.length()) {
                                            val spanObj = spansArr.getJSONObject(s)
                                            val typeName = spanObj.optString("type")
                                            val spanType = try {
                                                RichSpanType.valueOf(typeName)
                                            } catch (_: Exception) {
                                                null
                                            }
                                            if (spanType != null) {
                                                spans.add(
                                                    RichSpan(
                                                        type = spanType,
                                                        start = spanObj.optInt("start", 0),
                                                        end = spanObj.optInt("end", 0),
                                                        payload = if (spanObj.has("payload")) spanObj.getString("payload") else null
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    blocks.add(NotesnookBlock.Text(id = id, text = text, spans = spans))
                                }
                                "table" -> {
                                    val rows = obj.optInt("rows", 2)
                                    val cols = obj.optInt("cols", 2)
                                    val data = mutableListOf<MutableList<String>>()
                                    val dataArr = obj.optJSONArray("data")
                                    for (r in 0 until rows) {
                                        val rowList = mutableListOf<String>()
                                        val rowArr = dataArr?.optJSONArray(r)
                                        for (c in 0 until cols) {
                                            rowList.add(rowArr?.optString(c, "") ?: "")
                                        }
                                        data.add(rowList)
                                    }
                                    blocks.add(NotesnookBlock.Table(id = id, rows = rows, cols = cols, data = data))
                                }
                                "horizontal_rule" -> {
                                    blocks.add(NotesnookBlock.HorizontalRule(id = id))
                                }
                                "code" -> {
                                    blocks.add(
                                        NotesnookBlock.Code(
                                            id = id,
                                            language = obj.optString("language", "Kotlin"),
                                            code = obj.optString("code", "")
                                        )
                                    )
                                }
                                "math" -> {
                                    blocks.add(
                                        NotesnookBlock.MathFormula(
                                            id = id,
                                            formula = obj.optString("formula", "E = mc²"),
                                            isInline = obj.optBoolean("isInline", false)
                                        )
                                    )
                                }
                                "callout" -> {
                                    blocks.add(
                                        NotesnookBlock.Callout(
                                            id = id,
                                            calloutType = obj.optString("calloutType", "note"),
                                            text = obj.optString("text", "")
                                        )
                                    )
                                }
                                "quote" -> {
                                    blocks.add(
                                        NotesnookBlock.Quote(
                                            id = id,
                                            text = obj.optString("text", "")
                                        )
                                    )
                                }
                                "outline" -> {
                                    blocks.add(
                                        NotesnookBlock.OutlineItem(
                                            id = id,
                                            level = obj.optInt("level", 0),
                                            text = obj.optString("text", ""),
                                            isCollapsed = obj.optBoolean("isCollapsed", false),
                                            isNumbered = obj.optBoolean("isNumbered", true)
                                        )
                                    )
                                }
                                "embed" -> {
                                    blocks.add(
                                        NotesnookBlock.Embed(
                                            id = id,
                                            type = obj.optString("embedType", "YouTube"),
                                            url = obj.optString("url", ""),
                                            title = obj.optString("title", "")
                                        )
                                    )
                                }
                                "attachment" -> {
                                    blocks.add(
                                        NotesnookBlock.Attachment(
                                            id = id,
                                            uri = obj.optString("uri", ""),
                                            fileName = obj.optString("fileName", "attachment.pdf"),
                                            fileSize = obj.optString("fileSize", "1.2 MB")
                                        )
                                    )
                                }
                                "image" -> {
                                    blocks.add(
                                        NotesnookBlock.Image(
                                            id = id,
                                            uri = obj.optString("uri", ""),
                                            caption = obj.optString("caption", "")
                                        )
                                    )
                                }
                            }
                        }
                        if (blocks.isNotEmpty()) return blocks
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Check for legacy TABLE_START tags
        if (raw.contains(TABLE_START) && raw.contains(TABLE_END)) {
            val blocks = mutableListOf<NotesnookBlock>()
            var curIdx = 0
            while (curIdx < raw.length) {
                val start = raw.indexOf(TABLE_START, curIdx)
                if (start == -1) {
                    val remaining = raw.substring(curIdx)
                    if (remaining.isNotEmpty()) {
                        val parsed = RichTextEngine.parse(remaining)
                        blocks.add(NotesnookBlock.Text(text = parsed.first, spans = parsed.second))
                    }
                    break
                }
                if (start > curIdx) {
                    val textBefore = raw.substring(curIdx, start)
                    val parsed = RichTextEngine.parse(textBefore)
                    blocks.add(NotesnookBlock.Text(text = parsed.first, spans = parsed.second))
                }
                val end = raw.indexOf(TABLE_END, start)
                if (end != -1) {
                    val jsonStr = raw.substring(start + TABLE_START.length, end)
                    try {
                        val jsonObj = JSONObject(jsonStr)
                        val id = jsonObj.optString("id", UUID.randomUUID().toString())
                        val rows = jsonObj.optInt("rows", 2)
                        val cols = jsonObj.optInt("cols", 2)
                        val dataArr = jsonObj.optJSONArray("data")
                        val tableData = mutableListOf<MutableList<String>>()
                        for (r in 0 until rows) {
                            val rowList = mutableListOf<String>()
                            val rowArr = dataArr?.optJSONArray(r)
                            for (c in 0 until cols) {
                                rowList.add(rowArr?.optString(c, "") ?: "")
                            }
                            tableData.add(rowList)
                        }
                        blocks.add(NotesnookBlock.Table(id = id, rows = rows, cols = cols, data = tableData))
                    } catch (_: Exception) {}
                    curIdx = end + TABLE_END.length
                } else {
                    curIdx = start + TABLE_START.length
                }
            }
            if (blocks.isNotEmpty()) return blocks
        }

        // 3. Fallback: Parse as standard Rich Text
        val parsed = RichTextEngine.parse(raw)
        return listOf(NotesnookBlock.Text(text = parsed.first, spans = parsed.second))
    }

    /**
     * Converts a list of blocks to clean human-readable text for card preview and search.
     */
    fun toPlainText(blocks: List<NotesnookBlock>): String {
        val sb = StringBuilder()
        for ((idx, block) in blocks.withIndex()) {
            when (block) {
                is NotesnookBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(block.text)
                    }
                }
                is NotesnookBlock.Table -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("📊 [Table ${block.rows}×${block.cols}]")
                }
                is NotesnookBlock.HorizontalRule -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("────────")
                }
                is NotesnookBlock.Code -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("💻 [Code: ${block.language}]")
                    if (block.code.isNotBlank()) {
                        sb.append("\n").append(block.code.take(120))
                    }
                }
                is NotesnookBlock.MathFormula -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("∑ ${block.formula}")
                }
                is NotesnookBlock.Callout -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    val emoji = when (block.calloutType.lowercase()) {
                        "warning" -> "⚠️"
                        "important" -> "📌"
                        "tip" -> "🚀"
                        "info" -> "ℹ️"
                        "success" -> "✅"
                        else -> "💡"
                    }
                    sb.append("$emoji ${block.text}")
                }
                is NotesnookBlock.Quote -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("“ ${block.text}")
                }
                is NotesnookBlock.OutlineItem -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    val indent = "  ".repeat(block.level.coerceIn(0, 5))
                    sb.append("$indent• ${block.text}")
                }
                is NotesnookBlock.Embed -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("▶️ ${block.type}: ${block.title.ifBlank { block.url }}")
                }
                is NotesnookBlock.Attachment -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("📎 ${block.fileName} (${block.fileSize})")
                }
                is NotesnookBlock.Image -> {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("🖼️ ${block.caption.ifBlank { "Image" }}")
                }
            }
        }
        return sb.toString()
    }

    /**
     * Converts raw note content (which may contain blocks) to clean plain text.
     */
    fun toPlainText(rawContent: String): String {
        return if (rawContent.contains(BLOCKS_PREFIX) || rawContent.contains(TABLE_START)) {
            toPlainText(parse(rawContent))
        } else {
            RichTextEngine.parse(rawContent).first
        }
    }
}
