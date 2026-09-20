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

import com.focusbyrj.app.data.note.ChecklistItem
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates standards-compliant Microsoft Word (.docx / OpenXML) documents
 * without heavy external dependencies using native zip packaging.
 */
object ArticleDocxGenerator {

    fun generateDocx(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // 1. [Content_Types].xml
            addZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())

            // 2. _rels/.rels
            addZipEntry(zip, "_rels/.rels", buildRootRelsXml())

            // 3. word/_rels/document.xml.rels
            addZipEntry(zip, "word/_rels/document.xml.rels", buildDocumentRelsXml())

            // 4. word/styles.xml
            addZipEntry(zip, "word/styles.xml", buildStylesXml())

            // 5. word/document.xml
            val docXml = buildDocumentXml(title, blocks, fallbackContent, isChecklist, checklistItems)
            addZipEntry(zip, "word/document.xml", docXml)
        }
        return baos.toByteArray()
    }

    private fun addZipEntry(zip: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""".trimIndent()
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".trimIndent()
    }

    private fun buildDocumentRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Segoe UI" w:hAnsi="Segoe UI" w:cs="Segoe UI"/>
        <w:sz w:val="23"/>
        <w:szCs w:val="23"/>
        <w:color w:val="1F2937"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:line="320" w:lineRule="auto" w:after="160"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:pPr>
      <w:spacing w:before="0" w:after="240"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="52"/>
      <w:szCs w:val="52"/>
      <w:color w:val="0F172A"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="Heading 1"/>
    <w:pPr>
      <w:spacing w:before="360" w:after="160"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="40"/>
      <w:szCs w:val="40"/>
      <w:color w:val="0F172A"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="Heading 2"/>
    <w:pPr>
      <w:spacing w:before="280" w:after="120"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="32"/>
      <w:szCs w:val="32"/>
      <w:color w:val="1E293B"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="Heading 3"/>
    <w:pPr>
      <w:spacing w:before="220" w:after="100"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="28"/>
      <w:szCs w:val="28"/>
      <w:color w:val="334155"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Quote">
    <w:name w:val="Quote"/>
    <w:pPr>
      <w:pBdr>
        <w:left w:val="single" w:sz="24" w:space="12" w:color="22C55E"/>
      </w:pBdr>
      <w:ind w:left="400" w:right="400"/>
      <w:spacing w:before="160" w:after="160"/>
    </w:pPr>
    <w:rPr>
      <w:i/>
      <w:color w:val="475569"/>
    </w:rPr>
  </w:style>
</w:styles>""".trimIndent()
    }

    private fun buildDocumentXml(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        isChecklist: Boolean,
        checklistItems: List<ChecklistItem>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
""")

        // Title
        if (title.isNotBlank()) {
            sb.append("    <w:p>\n")
            sb.append("      <w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>\n")
            sb.append("      <w:r><w:t>").append(escapeXml(title)).append("</w:t></w:r>\n")
            sb.append("    </w:p>\n")
        }

        if (isChecklist && checklistItems.isNotEmpty()) {
            checklistItems.forEach { item ->
                val box = if (item.isChecked) "☑  " else "☐  "
                sb.append("    <w:p>\n")
                sb.append("      <w:pPr><w:ind w:left=\"360\"/></w:pPr>\n")
                sb.append("      <w:r><w:rPr><w:b/><w:color w:val=\"").append(if (item.isChecked) "22C55E" else "6B7280").append("\"/></w:rPr><w:t>").append(box).append("</w:t></w:r>\n")
                sb.append("      <w:r><w:rPr>")
                if (item.isChecked) sb.append("<w:strike/><w:color w:val=\"9CA3AF\"/>")
                sb.append("</w:rPr><w:t>").append(escapeXml(item.text)).append("</w:t></w:r>\n")
                sb.append("    </w:p>\n")
            }
        } else if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            val text = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).text else fallbackContent
            val spans = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).spans else emptyList()
            if (text.isNotBlank()) {
                val lines = text.split("\n")
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        sb.append(renderParagraphWithSpans(line, spans))
                    } else {
                        sb.append("    <w:p/>\n")
                    }
                }
            }
        } else {
            blocks.forEach { block ->
                when (block) {
                    is NotesnookBlock.Text -> {
                        if (block.text.isNotBlank()) {
                            block.text.split("\n").forEach { line ->
                                if (line.isNotBlank()) {
                                    sb.append(renderParagraphWithSpans(line, block.spans))
                                } else {
                                    sb.append("    <w:p/>\n")
                                }
                            }
                        }
                    }
                    is NotesnookBlock.OutlineItem -> {
                        val indent = (block.level + 1) * 360
                        val bulletSymbol = if (block.isNumbered) "• " else "– "
                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr><w:ind w:left=\"").append(indent).append("\"/></w:pPr>\n")
                        sb.append("      <w:r><w:rPr><w:b/><w:color w:val=\"3B82F6\"/></w:rPr><w:t>").append(bulletSymbol).append("</w:t></w:r>\n")
                        sb.append("      <w:r><w:t>").append(escapeXml(block.text)).append("</w:t></w:r>\n")
                        sb.append("    </w:p>\n")
                    }
                    is NotesnookBlock.Callout -> {
                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr>\n")
                        sb.append("        <w:pBdr><w:left w:val=\"single\" w:sz=\"24\" w:space=\"12\" w:color=\"3B82F6\"/></w:pPr>\n")
                        sb.append("        <w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EFF6FF\"/>\n")
                        sb.append("        <w:ind w:left=\"360\" w:right=\"360\"/>\n")
                        sb.append("      </w:pPr>\n")
                        sb.append("      <w:r><w:rPr><w:b/><w:color w:val=\"1D4ED8\"/></w:rPr><w:t>[").append(escapeXml(block.calloutType.uppercase())).append("] </w:t></w:r>\n")
                        sb.append("      <w:r><w:t>").append(escapeXml(block.text)).append("</w:t></w:r>\n")
                        sb.append("    </w:p>\n")
                    }
                    is NotesnookBlock.Quote -> {
                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr><w:pStyle w:val=\"Quote\"/></w:pPr>\n")
                        sb.append("      <w:r><w:t>").append(escapeXml(block.text)).append("</w:t></w:r>\n")
                        sb.append("    </w:p>\n")
                    }
                    is NotesnookBlock.Code -> {
                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr>\n")
                        sb.append("        <w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F3F4F6\"/>\n")
                        sb.append("        <w:ind w:left=\"240\" w:right=\"240\"/>\n")
                        sb.append("        <w:spacing w:line=\"240\" w:lineRule=\"auto\" w:before=\"80\" w:after=\"80\"/>\n")
                        sb.append("      </w:pPr>\n")
                        sb.append("      <w:r><w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"19\"/><w:color w:val=\"1F2937\"/></w:rPr><w:t xml:space=\"preserve\">")
                            .append(escapeXml(block.code)).append("</w:t></w:r>\n")
                        sb.append("    </w:p>\n")
                    }
                    is NotesnookBlock.HorizontalRule -> {
                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"E5E7EB\"/></w:pPr></w:pPr>\n")
                        sb.append("    </w:p>\n")
                    }
                    is NotesnookBlock.MathFormula -> {
                        sb.append("    <w:p>\n")
                        sb.append("      <w:pPr>\n")
                        sb.append("        <w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"12\" w:color=\"22C55E\"/></w:pPr>\n")
                        sb.append("        <w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F0FDF4\"/>\n")
                        sb.append("        <w:ind w:left=\"360\" w:right=\"360\"/>\n")
                        sb.append("        <w:spacing w:before=\"120\" w:after=\"120\"/>\n")
                        sb.append("      </w:pPr>\n")
                        sb.append("      <w:r><w:rPr><w:b/><w:color w:val=\"16A34A\"/></w:rPr><w:t>[MATH] </w:t></w:r>\n")
                        sb.append("      <w:r><w:rPr><w:i/><w:rFonts w:ascii=\"Cambria Math\" w:hAnsi=\"Cambria Math\"/><w:sz w:val=\"26\"/></w:rPr><w:t>").append(escapeXml(block.formula.ifBlank { "E = mc²" })).append("</w:t></w:r>\n")
                        sb.append("    </w:p>\n")
                    }
                    is NotesnookBlock.Table -> {
                        if (block.data.isNotEmpty()) {
                            sb.append("    <w:tbl>\n")
                            sb.append("      <w:tblPr>\n")
                            sb.append("        <w:tblW w:w=\"0\" w:type=\"auto\"/>\n")
                            sb.append("        <w:tblBorders>\n")
                            sb.append("          <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D1D5DB\"/>\n")
                            sb.append("          <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D1D5DB\"/>\n")
                            sb.append("          <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D1D5DB\"/>\n")
                            sb.append("          <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D1D5DB\"/>\n")
                            sb.append("          <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E5E7EB\"/>\n")
                            sb.append("          <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"E5E7EB\"/>\n")
                            sb.append("        </w:tblBorders>\n")
                            sb.append("      </w:tblPr>\n")

                            block.data.forEachIndexed { rowIndex, row ->
                                sb.append("      <w:tr>\n")
                                row.forEach { cell ->
                                    sb.append("        <w:tc>\n")
                                    sb.append("          <w:tcPr>\n")
                                    if (rowIndex == 0) {
                                        sb.append("            <w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F9FAFB\"/>\n")
                                    }
                                    sb.append("            <w:tcMar><w:top w:w=\"120\"/><w:bottom w:w=\"120\"/><w:left w:w=\"160\"/><w:right w:w=\"160\"/></w:tcMar>\n")
                                    sb.append("          </w:tcPr>\n")
                                    sb.append("          <w:p>\n")
                                    sb.append("            <w:r><w:rPr>")
                                    if (rowIndex == 0) sb.append("<w:b/>")
                                    sb.append("</w:rPr><w:t>").append(escapeXml(cell)).append("</w:t></w:r>\n")
                                    sb.append("          </w:p>\n")
                                    sb.append("        </w:tc>\n")
                                }
                                sb.append("      </w:tr>\n")
                            }
                            sb.append("    </w:tbl>\n")
                        }
                    }
                    else -> {}
                }
            }
        }

        // Page setup
        sb.append("""    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>""")

        return sb.toString()
    }

    private fun renderParagraphWithSpans(text: String, spans: List<RichSpan>): String {
        val lineHeaderSpan = spans.firstOrNull {
            it.type in setOf(
                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
            )
        }

        val styleVal = when (lineHeaderSpan?.type) {
            RichSpanType.HEADING_1 -> "Heading1"
            RichSpanType.HEADING_2 -> "Heading2"
            RichSpanType.HEADING_3, RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6 -> "Heading3"
            else -> null
        }

        val sb = StringBuilder()
        sb.append("    <w:p>\n")
        if (styleVal != null) {
            sb.append("      <w:pPr><w:pStyle w:val=\"").append(styleVal).append("\"/></w:pPr>\n")
        }

        val inlineSpans = spans.filterNot {
            it.type in setOf(
                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
            )
        }

        if (inlineSpans.isEmpty()) {
            sb.append("      <w:r><w:t>").append(escapeXml(text)).append("</w:t></w:r>\n")
        } else {
            val sorted = inlineSpans.sortedWith(compareBy({ it.start }, { -it.end }))
            var lastIdx = 0
            sorted.forEach { span ->
                val s = span.start.coerceIn(0, text.length)
                val e = span.end.coerceIn(s, text.length)
                if (s > lastIdx) {
                    sb.append("      <w:r><w:t>").append(escapeXml(text.substring(lastIdx, s))).append("</w:t></w:r>\n")
                }
                val slice = text.substring(s, e)
                sb.append("      <w:r>\n")
                sb.append("        <w:rPr>")
                when (span.type) {
                    RichSpanType.BOLD -> sb.append("<w:b/>")
                    RichSpanType.ITALIC -> sb.append("<w:i/>")
                    RichSpanType.UNDERLINE -> sb.append("<w:u w:val=\"single\"/>")
                    RichSpanType.STRIKETHROUGH -> sb.append("<w:strike/>")
                    RichSpanType.HIGHLIGHT -> sb.append("<w:highlight w:val=\"yellow\"/>")
                    RichSpanType.CODE -> sb.append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F3F4F6\"/>")
                    RichSpanType.SUBSCRIPT -> sb.append("<w:vertAlign w:val=\"subscript\"/>")
                    RichSpanType.SUPERSCRIPT -> sb.append("<w:vertAlign w:val=\"superscript\"/>")
                    else -> {}
                }
                sb.append("</w:rPr>\n")
                sb.append("        <w:t>").append(escapeXml(slice)).append("</w:t>\n")
                sb.append("      </w:r>\n")
                lastIdx = e
            }
            if (lastIdx < text.length) {
                sb.append("      <w:r><w:t>").append(escapeXml(text.substring(lastIdx))).append("</w:t></w:r>\n")
            }
        }

        sb.append("    </w:p>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
