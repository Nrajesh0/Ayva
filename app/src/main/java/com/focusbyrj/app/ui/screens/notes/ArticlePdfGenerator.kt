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

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.focusbyrj.app.data.note.ChecklistItem
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates beautiful, multi-page vector PDF documents natively using Android's PdfDocument.
 * Supports all Notesnook-grade rich article blocks: Table, Math Formulas, Code, Callouts, Quotes, etc.
 */
object ArticlePdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width in points
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN_LEFT = 48f
    private const val MARGIN_RIGHT = 48f
    private const val MARGIN_TOP = 48f
    private const val MARGIN_BOTTOM = 48f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

    fun generatePdf(
        title: String,
        blocks: List<NotesnookBlock>,
        fallbackContent: String,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList()
    ): ByteArray {
        val pdfDoc = PdfDocument()

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var currentPage = pdfDoc.startPage(pageInfo)
        var canvas = currentPage.canvas

        var currentY = MARGIN_TOP

        fun ensureSpace(neededHeight: Float) {
            if (currentY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                pdfDoc.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                currentPage = pdfDoc.startPage(pageInfo)
                canvas = currentPage.canvas
                currentY = MARGIN_TOP
            }
        }

        // --- PAINTS ---
        val titlePaint = TextPaint().apply {
            color = Color.rgb(15, 23, 42) // #0F172A
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val metaPaint = TextPaint().apply {
            color = Color.rgb(100, 116, 139) // #64748B
            textSize = 9f
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val h1Paint = TextPaint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 17f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val h2Paint = TextPaint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bodyPaint = TextPaint().apply {
            color = Color.rgb(31, 41, 55) // #1F2937
            textSize = 10f
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val codePaint = TextPaint().apply {
            color = Color.rgb(241, 245, 249)
            textSize = 9.5f
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        val mathPaint = TextPaint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 13f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        val badgePaint = TextPaint().apply {
            color = Color.rgb(34, 197, 94)
            textSize = 8.5f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val tableHeaderPaint = TextPaint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val tableCellPaint = TextPaint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240) // #E2E8F0
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val tableBorderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // #CBD5E1
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val bgFillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // 1. Draw Title / Header
        if (title.isNotBlank()) {
            val titleLayout = createStaticLayout(title, titlePaint, CONTENT_WIDTH.toInt())
            ensureSpace(titleLayout.height.toFloat() + 32f)
            canvas.save()
            canvas.translate(MARGIN_LEFT, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 6f

            // Date metadata
            val dateStr = "Exported from Focus Notes • " + SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
            val metaLayout = createStaticLayout(dateStr, metaPaint, CONTENT_WIDTH.toInt())
            canvas.save()
            canvas.translate(MARGIN_LEFT, currentY)
            metaLayout.draw(canvas)
            canvas.restore()
            currentY += metaLayout.height + 10f

            // Top decorative divider line
            canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY, linePaint)
            currentY += 16f
        } else {
            // Minimal header line
            val dateStr = "Focus Notes • " + SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
            val metaLayout = createStaticLayout(dateStr, metaPaint, CONTENT_WIDTH.toInt())
            canvas.save()
            canvas.translate(MARGIN_LEFT, currentY)
            metaLayout.draw(canvas)
            canvas.restore()
            currentY += metaLayout.height + 8f
            canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY, linePaint)
            currentY += 14f
        }

        // 2. Render Checklists or Blocks
        if (isChecklist && checklistItems.isNotEmpty()) {
            checklistItems.forEach { item ->
                val itemText = item.text
                val textLayout = createStaticLayout(itemText, bodyPaint, (CONTENT_WIDTH - 24f).toInt())
                val rowHeight = maxOf(textLayout.height.toFloat(), 18f) + 6f
                ensureSpace(rowHeight)

                val boxSize = 10f
                val boxRect = RectF(MARGIN_LEFT, currentY + 2f, MARGIN_LEFT + boxSize, currentY + 2f + boxSize)
                if (item.isChecked) {
                    bgFillPaint.color = Color.rgb(34, 197, 94) // Green
                    canvas.drawRoundRect(boxRect, 2f, 2f, bgFillPaint)
                    val checkPaint = Paint().apply {
                        color = Color.WHITE
                        strokeWidth = 1.5f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    canvas.drawLine(boxRect.left + 2f, boxRect.centerY(), boxRect.left + 4.5f, boxRect.bottom - 2.5f, checkPaint)
                    canvas.drawLine(boxRect.left + 4.5f, boxRect.bottom - 2.5f, boxRect.right - 2f, boxRect.top + 2.5f, checkPaint)
                } else {
                    val boxBorder = Paint().apply {
                        color = Color.rgb(156, 163, 175)
                        strokeWidth = 1.2f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(boxRect, 2f, 2f, boxBorder)
                }

                canvas.save()
                canvas.translate(MARGIN_LEFT + 20f, currentY)
                textLayout.draw(canvas)
                canvas.restore()

                currentY += rowHeight
            }
        } else if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            val text = if (blocks.isNotEmpty()) (blocks[0] as NotesnookBlock.Text).text else fallbackContent
            val lines = text.split("\n")
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    val layout = createStaticLayout(line, bodyPaint, CONTENT_WIDTH.toInt())
                    ensureSpace(layout.height.toFloat() + 8f)
                    canvas.save()
                    canvas.translate(MARGIN_LEFT, currentY)
                    layout.draw(canvas)
                    canvas.restore()
                    currentY += layout.height + 8f
                } else {
                    currentY += 8f
                }
            }
        } else {
            blocks.forEach { block ->
                when (block) {
                    is NotesnookBlock.Text -> {
                        if (block.text.isNotBlank()) {
                            val isHeading1 = block.spans.any { it.type == RichSpanType.HEADING_1 }
                            val isHeading2 = block.spans.any { it.type in setOf(RichSpanType.HEADING_2, RichSpanType.HEADING_3) }
                            val paint = when {
                                isHeading1 -> h1Paint
                                isHeading2 -> h2Paint
                                else -> bodyPaint
                            }
                            val layout = createStaticLayout(block.text, paint, CONTENT_WIDTH.toInt())
                            val spaceNeeded = layout.height.toFloat() + (if (isHeading1 || isHeading2) 12f else 6f)
                            ensureSpace(spaceNeeded)
                            canvas.save()
                            canvas.translate(MARGIN_LEFT, currentY)
                            layout.draw(canvas)
                            canvas.restore()
                            currentY += spaceNeeded
                        }
                    }
                    is NotesnookBlock.OutlineItem -> {
                        if (block.text.isNotBlank()) {
                            val indent = (block.level + 1) * 14f
                            val layout = createStaticLayout(block.text, bodyPaint, (CONTENT_WIDTH - indent - 14f).toInt())
                            val needed = layout.height.toFloat() + 5f
                            ensureSpace(needed)

                            val bulletPaint = Paint().apply {
                                color = Color.rgb(34, 197, 94)
                                style = Paint.Style.FILL
                                isAntiAlias = true
                            }
                            canvas.drawCircle(MARGIN_LEFT + indent + 4f, currentY + 6f, 2.5f, bulletPaint)

                            canvas.save()
                            canvas.translate(MARGIN_LEFT + indent + 12f, currentY)
                            layout.draw(canvas)
                            canvas.restore()
                            currentY += needed
                        }
                    }
                    is NotesnookBlock.Callout -> {
                        if (block.text.isNotBlank()) {
                            val layout = createStaticLayout(block.text, bodyPaint, (CONTENT_WIDTH - 28f).toInt())
                            val boxHeight = layout.height.toFloat() + 16f
                            ensureSpace(boxHeight + 8f)

                            val rect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY + boxHeight)
                            bgFillPaint.color = when (block.calloutType.lowercase()) {
                                "warning" -> Color.rgb(254, 243, 199) // Amber #FEF3C7
                                "important" -> Color.rgb(254, 226, 226) // Red #FEE2E2
                                "tip" -> Color.rgb(220, 252, 231) // Green #DCFCE7
                                "info" -> Color.rgb(243, 232, 255) // Purple #F3E8FF
                                else -> Color.rgb(239, 246, 255) // Blue #EFF6FF
                            }
                            canvas.drawRoundRect(rect, 6f, 6f, bgFillPaint)

                            val accentColor = when (block.calloutType.lowercase()) {
                                "warning" -> Color.rgb(217, 119, 6)
                                "important" -> Color.rgb(220, 38, 38)
                                "tip" -> Color.rgb(22, 163, 74)
                                "info" -> Color.rgb(147, 51, 234)
                                else -> Color.rgb(37, 99, 235)
                            }
                            val accentBar = Paint().apply {
                                color = accentColor
                                style = Paint.Style.FILL
                                isAntiAlias = true
                            }
                            canvas.drawRoundRect(RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + 4f, currentY + boxHeight), 2f, 2f, accentBar)

                            canvas.save()
                            canvas.translate(MARGIN_LEFT + 14f, currentY + 8f)
                            layout.draw(canvas)
                            canvas.restore()
                            currentY += boxHeight + 10f
                        }
                    }
                    is NotesnookBlock.Quote -> {
                        if (block.text.isNotBlank()) {
                            val quotePaint = TextPaint(bodyPaint).apply {
                                color = Color.rgb(71, 85, 105)
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            }
                            val layout = createStaticLayout(block.text, quotePaint, (CONTENT_WIDTH - 24f).toInt())
                            val boxHeight = layout.height.toFloat() + 10f
                            ensureSpace(boxHeight + 8f)

                            val quoteBar = Paint().apply {
                                color = Color.rgb(34, 197, 94) // Green
                                strokeWidth = 3f
                                style = Paint.Style.STROKE
                                isAntiAlias = true
                            }
                            canvas.drawLine(MARGIN_LEFT + 2f, currentY + 2f, MARGIN_LEFT + 2f, currentY + boxHeight - 2f, quoteBar)

                            canvas.save()
                            canvas.translate(MARGIN_LEFT + 14f, currentY + 4f)
                            layout.draw(canvas)
                            canvas.restore()
                            currentY += boxHeight + 8f
                        }
                    }
                    is NotesnookBlock.Code -> {
                        // Render full code block with top language header bar
                        val codeContent = if (block.code.isNotBlank()) block.code else "// Code block (${block.language})"
                        val codeLayout = createStaticLayout(codeContent, codePaint, (CONTENT_WIDTH - 24f).toInt())
                        val headerHeight = 20f
                        val boxHeight = headerHeight + codeLayout.height.toFloat() + 16f
                        ensureSpace(boxHeight + 10f)

                        val rect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY + boxHeight)
                        bgFillPaint.color = Color.rgb(30, 41, 59) // Slate-800 #1E293B
                        canvas.drawRoundRect(rect, 6f, 6f, bgFillPaint)

                        // Top header row for language
                        val headerBg = Paint().apply {
                            color = Color.rgb(15, 23, 42) // Slate-900 #0F172A
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY + headerHeight), 6f, 6f, headerBg)
                        canvas.drawRect(RectF(MARGIN_LEFT, currentY + headerHeight - 4f, MARGIN_LEFT + CONTENT_WIDTH, currentY + headerHeight), headerBg)

                        val langPaint = TextPaint().apply {
                            color = Color.rgb(96, 165, 250) // Blue-400
                            textSize = 8.5f
                            isAntiAlias = true
                            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        }
                        canvas.drawText(block.language.ifBlank { "Code" }.uppercase(), MARGIN_LEFT + 10f, currentY + 13.5f, langPaint)

                        canvas.save()
                        canvas.translate(MARGIN_LEFT + 12f, currentY + headerHeight + 8f)
                        codeLayout.draw(canvas)
                        canvas.restore()
                        currentY += boxHeight + 10f
                    }
                    is NotesnookBlock.MathFormula -> {
                        // Render full KaTeX / Math formula block
                        val formulaText = if (block.formula.isNotBlank()) block.formula else "E = mc²"
                        val formulaLayout = createStaticLayout(formulaText, mathPaint, (CONTENT_WIDTH - 28f).toInt())
                        val headerHeight = 18f
                        val boxHeight = headerHeight + formulaLayout.height.toFloat() + 14f
                        ensureSpace(boxHeight + 8f)

                        val rect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY + boxHeight)
                        bgFillPaint.color = Color.rgb(248, 250, 252) // #F8FAFC
                        canvas.drawRoundRect(rect, 6f, 6f, bgFillPaint)

                        val borderPaint = Paint().apply {
                            color = Color.rgb(226, 232, 240) // #E2E8F0
                            strokeWidth = 1f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

                        // KaTeX Math badge tag
                        val badgeRect = RectF(MARGIN_LEFT + 10f, currentY + 5f, MARGIN_LEFT + 80f, currentY + headerHeight)
                        bgFillPaint.color = Color.rgb(220, 252, 231) // Green-100 #DCFCE7
                        canvas.drawRoundRect(badgeRect, 3f, 3f, bgFillPaint)
                        badgePaint.color = Color.rgb(22, 163, 74)
                        canvas.drawText("∑ KaTeX Math", MARGIN_LEFT + 14f, currentY + 14f, badgePaint)

                        canvas.save()
                        canvas.translate(MARGIN_LEFT + 14f, currentY + headerHeight + 6f)
                        formulaLayout.draw(canvas)
                        canvas.restore()
                        currentY += boxHeight + 8f
                    }
                    is NotesnookBlock.HorizontalRule -> {
                        ensureSpace(16f)
                        canvas.drawLine(MARGIN_LEFT, currentY + 8f, MARGIN_LEFT + CONTENT_WIDTH, currentY + 8f, linePaint)
                        currentY += 16f
                    }
                    is NotesnookBlock.Table -> {
                        if (block.data.isNotEmpty()) {
                            val colCount = block.data.maxOfOrNull { it.size } ?: 1
                            val colWidth = CONTENT_WIDTH / colCount

                            // Draw table row by row with dynamic height and clear borders
                            block.data.forEachIndexed { rowIndex, row ->
                                // Pre-measure cell layouts for this row
                                val cellLayouts = (0 until colCount).map { colIndex ->
                                    val cellText = row.getOrNull(colIndex) ?: ""
                                    val p = if (rowIndex == 0) tableHeaderPaint else tableCellPaint
                                    createStaticLayout(cellText, p, (colWidth - 10f).toInt())
                                }
                                val maxCellHeight = cellLayouts.maxOfOrNull { it.height.toFloat() } ?: 14f
                                val rowHeight = maxOf(maxCellHeight + 10f, 22f)

                                ensureSpace(rowHeight)

                                val rowTop = currentY
                                val rowBottom = currentY + rowHeight

                                // Header row background
                                if (rowIndex == 0) {
                                    bgFillPaint.color = Color.rgb(241, 245, 249) // Slate-100 #F1F5F9
                                    canvas.drawRect(MARGIN_LEFT, rowTop, MARGIN_LEFT + CONTENT_WIDTH, rowBottom, bgFillPaint)
                                } else if (rowIndex % 2 == 1) {
                                    bgFillPaint.color = Color.rgb(250, 250, 250) // Subtle zebra striping
                                    canvas.drawRect(MARGIN_LEFT, rowTop, MARGIN_LEFT + CONTENT_WIDTH, rowBottom, bgFillPaint)
                                }

                                // Row top border (for first row) or row bottom border
                                if (rowIndex == 0) {
                                    canvas.drawLine(MARGIN_LEFT, rowTop, MARGIN_LEFT + CONTENT_WIDTH, rowTop, tableBorderPaint)
                                }
                                canvas.drawLine(MARGIN_LEFT, rowBottom, MARGIN_LEFT + CONTENT_WIDTH, rowBottom, tableBorderPaint)

                                // Draw vertical cell borders and text
                                (0 until colCount).forEach { colIndex ->
                                    val cellLeft = MARGIN_LEFT + (colIndex * colWidth)
                                    val cellRight = cellLeft + colWidth

                                    // Vertical line on left of every cell
                                    canvas.drawLine(cellLeft, rowTop, cellLeft, rowBottom, tableBorderPaint)
                                    if (colIndex == colCount - 1) {
                                        // Vertical line on right of last cell
                                        canvas.drawLine(cellRight, rowTop, cellRight, rowBottom, tableBorderPaint)
                                    }

                                    // Draw cell text
                                    val layout = cellLayouts[colIndex]
                                    canvas.save()
                                    canvas.translate(cellLeft + 5f, rowTop + 5f)
                                    layout.draw(canvas)
                                    canvas.restore()
                                }

                                currentY += rowHeight
                            }

                            currentY += 10f
                        }
                    }
                    is NotesnookBlock.Embed -> {
                        val embedHeight = 36f
                        ensureSpace(embedHeight + 8f)
                        val rect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY + embedHeight)
                        bgFillPaint.color = Color.rgb(248, 250, 252)
                        canvas.drawRoundRect(rect, 6f, 6f, bgFillPaint)
                        val border = Paint().apply {
                            color = Color.rgb(226, 232, 240)
                            strokeWidth = 1f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(rect, 6f, 6f, border)

                        val titleP = TextPaint().apply {
                            color = Color.rgb(15, 23, 42)
                            textSize = 10f
                            isAntiAlias = true
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        val urlP = TextPaint().apply {
                            color = Color.rgb(100, 116, 139)
                            textSize = 8.5f
                            isAntiAlias = true
                        }
                        canvas.drawText(block.title.ifBlank { "Media Embed" }, MARGIN_LEFT + 12f, currentY + 15f, titleP)
                        canvas.drawText(block.url, MARGIN_LEFT + 12f, currentY + 28f, urlP)
                        currentY += embedHeight + 8f
                    }
                    is NotesnookBlock.Attachment -> {
                        val attachHeight = 36f
                        ensureSpace(attachHeight + 8f)
                        val rect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + CONTENT_WIDTH, currentY + attachHeight)
                        bgFillPaint.color = Color.rgb(248, 250, 252)
                        canvas.drawRoundRect(rect, 6f, 6f, bgFillPaint)
                        val border = Paint().apply {
                            color = Color.rgb(226, 232, 240)
                            strokeWidth = 1f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(rect, 6f, 6f, border)

                        val nameP = TextPaint().apply {
                            color = Color.rgb(15, 23, 42)
                            textSize = 10f
                            isAntiAlias = true
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        val sizeP = TextPaint().apply {
                            color = Color.rgb(100, 116, 139)
                            textSize = 8.5f
                            isAntiAlias = true
                        }
                        canvas.drawText("📎 " + block.fileName, MARGIN_LEFT + 12f, currentY + 15f, nameP)
                        canvas.drawText(block.fileSize, MARGIN_LEFT + 12f, currentY + 28f, sizeP)
                        currentY += attachHeight + 8f
                    }
                    is NotesnookBlock.Image -> {
                        if (block.caption.isNotBlank()) {
                            val capLayout = createStaticLayout("[Image: ${block.caption}]", metaPaint, CONTENT_WIDTH.toInt())
                            ensureSpace(capLayout.height.toFloat() + 8f)
                            canvas.save()
                            canvas.translate(MARGIN_LEFT, currentY)
                            capLayout.draw(canvas)
                            canvas.restore()
                            currentY += capLayout.height + 8f
                        }
                    }
                }
            }
        }

        // Finish last active page before writing output
        pdfDoc.finishPage(currentPage)

        val baos = ByteArrayOutputStream()
        pdfDoc.writeTo(baos)
        pdfDoc.close()
        return baos.toByteArray()
    }

    private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        val safeWidth = maxOf(width, 10)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()
    }
}
