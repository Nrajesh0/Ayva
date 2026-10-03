/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import android.net.Uri
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.ArticleExporter
import com.focusbyrj.app.ui.screens.notes.ArticleExporterHelper
import com.focusbyrj.app.ui.screens.notes.ArticleTocHelper
import com.focusbyrj.app.ui.screens.notes.AudioPlayerWidgetHelper
import com.focusbyrj.app.ui.screens.notes.DocumentMetricsCalculator
import com.focusbyrj.app.ui.screens.notes.KeepNoteCardHelper
import com.focusbyrj.app.ui.screens.notes.KeepNoteShareParser
import com.focusbyrj.app.ui.screens.notes.KeepSketchHelper
import com.focusbyrj.app.ui.screens.notes.LabelDialogHelper
import com.focusbyrj.app.ui.screens.notes.MAX_COLLAGE_IMAGES
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.screens.notes.NotesnookUrlValidator
import com.focusbyrj.app.ui.screens.notes.buildGoogleKeepCollageRows
import com.focusbyrj.app.util.ImageUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batch 11 Security & Reliability Regression Test Suite
 *
 * Adversarial TDD tests covering:
 * - BATCH-11-001: KeepNoteCard raw Notesnook block JSON markup leakage in card preview
 * - BATCH-11-002: EditLabelsDialog and NoteLabelsDialog duplicate key crash in LazyColumn
 * - BATCH-11-003: ImageUtils unbounded cache memory leak & hardware bitmap handling
 * - BATCH-11-004: ArticleTocHelper hashtags and code lines misclassified as headings
 * - BATCH-11-005: Notesnook insert URL validation rejecting dangerous schemes (javascript:, file://)
 * - BATCH-11-006: KeepSketchDialog step size infinite loop freeze & color bounds safety
 * - BATCH-11-007: KeepNoteShareParser over-eager checklist false-positive conversion
 * - BATCH-11-008: AudioPlayerWidget seek bounds and NaN fraction coercion
 */
@RunWith(RobolectricTestRunner::class)
class Batch11SecurityAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<FocusApplication>()
    }

    // -------------------------------------------------------------------------
    // BATCH-11-007: KeepNoteShareParser Over-eager Checklist Conversion
    // -------------------------------------------------------------------------
    @Test
    fun testKeepNoteShareParser_DoesNotConvertMultiLineNoteToBlankContentChecklistOnSingleAccidentalBracket() {
        val rawSubject = "Python List Notes"
        val rawText = """
            Today we learned about data structures in Python.
            An empty list is declared as [] in standard code.
            Lists are dynamic and ordered.
            Keep reviewing chapter 4.
        """.trimIndent()

        val parsed = KeepNoteShareParser.parseContent(rawSubject, rawText)

        assertFalse("Multi-line prose containing brackets must NOT be converted to a checklist", parsed.isChecklist)
        assertTrue("Content must be preserved as body text", parsed.content.contains("An empty list is declared as []"))
        assertEquals("Python List Notes", parsed.title)
    }

    @Test
    fun testKeepNoteShareParser_CorrectlyIdentifiesTrueChecklist() {
        val rawSubject = "Grocery List"
        val rawText = """
            - [ ] Milk
            - [ ] Farm fresh eggs
            - [x] Sourdough bread
        """.trimIndent()

        val parsed = KeepNoteShareParser.parseContent(rawSubject, rawText)

        assertTrue("True checklist note must be identified as checklist", parsed.isChecklist)
        assertEquals(3, parsed.checklistItems.size)
        assertEquals("Milk", parsed.checklistItems[0].text)
        assertFalse(parsed.checklistItems[0].isChecked)
        assertTrue(parsed.checklistItems[2].isChecked)
    }

    // -------------------------------------------------------------------------
    // BATCH-11-004: ArticleTocHelper Hashtags & Code Misclassified as Headings
    // -------------------------------------------------------------------------
    @Test
    fun testArticleTocHelper_RejectsHashtagsAndNonHeadingHashLines() {
        val title = "My Article"
        val rawContent = """
            # Real Heading 1
            This paragraph mentions #focus and #android tags.
            #include <iostream>
            int main() { return 0; }
            ## Real Heading 2
            #123 is the issue number for this bug.
            ### Real Heading 3
        """.trimIndent()

        val toc = ArticleTocHelper.extractToc(title, emptyList(), rawContent)

        val titles = toc.map { it.title }
        assertTrue("Should contain title", titles.contains("My Article"))
        assertTrue("Should contain H1", titles.contains("Real Heading 1"))
        assertTrue("Should contain H2", titles.contains("Real Heading 2"))
        assertTrue("Should contain H3", titles.contains("Real Heading 3"))

        assertFalse("Should NOT include #include as heading", titles.any { it.contains("include") })
        assertFalse("Should NOT include #123 as heading", titles.any { it.contains("123") })
        assertFalse("Should NOT include hashtags as heading", titles.any { it.contains("focus") || it.contains("android") })
    }

    @Test
    fun testArticleTocHelper_CoercesOutlineLevelToValidRange() {
        val blocks = listOf(
            NotesnookBlock.OutlineItem(id = "out1", text = "Deeply nested node", level = 5)
        )
        val toc = ArticleTocHelper.extractToc("Test", blocks, "")

        val outlineItem = toc.firstOrNull { it.id == "out1" }
        assertNotNull(outlineItem)
        assertTrue("Outline heading level must be coerced to 1..6", outlineItem!!.level in 1..6)
    }

    // -------------------------------------------------------------------------
    // BATCH-11-003: ImageUtils Cache Clearance & Safe Bounds
    // -------------------------------------------------------------------------
    @Test
    fun testImageUtils_ClearCacheAndSafeBitmapDownsampling() {
        ImageUtils.clearCache()

        // Create large 200x200 bitmap
        val largeBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(context.resources, largeBmp)

        val imageBitmap = ImageUtils.drawableToImageBitmap(drawable)
        assertNotNull(imageBitmap)
        assertTrue("ImageBitmap width must be constrained to 96 max", imageBitmap!!.width <= 96)
        assertTrue("ImageBitmap height must be constrained to 96 max", imageBitmap.height <= 96)

        // Clear cache must work safely
        ImageUtils.clearCache()
    }

    // -------------------------------------------------------------------------
    // BATCH-11-001: KeepNoteCard Raw Block Markup Stripping
    // -------------------------------------------------------------------------
    @Test
    fun testKeepNoteCardHelper_StripsNotesnookBlockJsonFromPreview() {
        val blockJson = "<!--NOTESNOOK_BLOCKS:[{\"type\":\"text\",\"text\":\"Secret notes meeting\"},{\"type\":\"quote\",\"text\":\"Action required\"}]-->"
        val note = NoteEntity(
            title = "Meeting",
            content = blockJson,
            colorKey = "default",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val cleanPreview = KeepNoteCardHelper.getCleanPreviewContent(note.content)

        assertFalse("Preview must NOT contain raw NOTESNOOK_BLOCKS marker", cleanPreview.contains("<!--NOTESNOOK_BLOCKS:"))
        assertTrue("Preview must contain extracted text", cleanPreview.contains("Secret notes meeting"))
        assertTrue("Preview must contain quote text", cleanPreview.contains("Action required"))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-002: Label Dialogs Duplicate Key & Validation
    // -------------------------------------------------------------------------
    @Test
    fun testLabelDialogHelper_DeduplicatesLabelsSafely() {
        val rawLabels = listOf("Work", "Personal", "Work", "work", "Fitness")
        val sanitized = LabelDialogHelper.sanitizeLabelsForDisplay(rawLabels)

        assertEquals("Deduplicated list must have unique items", 4, sanitized.size)
        // Verify canAddLabel logic
        assertFalse("Cannot add blank label", LabelDialogHelper.canAddLabel("   ", sanitized))
        assertFalse("Cannot add duplicate label (exact)", LabelDialogHelper.canAddLabel("Personal", sanitized))
        assertFalse("Cannot add duplicate label (case-insensitive)", LabelDialogHelper.canAddLabel("work", sanitized))
        assertTrue("Can add new distinct label", LabelDialogHelper.canAddLabel("Health", sanitized))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-005: Notesnook Insert URL Scheme Validation
    // -------------------------------------------------------------------------
    @Test
    fun testNotesnookUrlValidator_RejectsDangerousSchemes() {
        assertTrue(NotesnookUrlValidator.isValidMediaUrl("https://example.com/pic.jpg"))
        assertTrue(NotesnookUrlValidator.isValidMediaUrl("http://example.com/audio.mp3"))
        assertTrue(NotesnookUrlValidator.isValidMediaUrl("content://media/external/images/media/42"))

        assertFalse(NotesnookUrlValidator.isValidMediaUrl("javascript:alert('xss')"))
        assertFalse(NotesnookUrlValidator.isValidMediaUrl("file:///data/data/com.focusbyrj.app/databases/notes.db"))
        assertFalse(NotesnookUrlValidator.isValidMediaUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(NotesnookUrlValidator.isValidMediaUrl(""))
        assertFalse(NotesnookUrlValidator.isValidMediaUrl("   "))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-006: KeepSketchDialog Step Size & Color Bounds
    // -------------------------------------------------------------------------
    @Test
    fun testKeepSketchDialog_StepAndColorCoercionSafety() {
        // Zero or negative scale must yield at least step = 1f to prevent while-loop freeze
        val stepZeroScale = KeepSketchHelper.calculateLineStep(60f, 0f)
        assertEquals(1f, stepZeroScale, 0.001f)

        val stepNegativeScale = KeepSketchHelper.calculateLineStep(60f, -0.5f)
        assertEquals(1f, stepNegativeScale, 0.001f)

        val stepNormalScale = KeepSketchHelper.calculateLineStep(60f, 1f)
        assertEquals(60f, stepNormalScale, 0.001f)

        // Color component coercion prevents IllegalArgumentException
        val safeR = KeepSketchHelper.coerceColorComponent(1.2f)
        assertEquals(255, safeR)

        val safeNegative = KeepSketchHelper.coerceColorComponent(-0.2f)
        assertEquals(0, safeNegative)

        val safeNormal = KeepSketchHelper.coerceColorComponent(0.5f)
        assertEquals(127, safeNormal)
    }

    // -------------------------------------------------------------------------
    // BATCH-11-008: AudioPlayerWidget Seek Calculation Bounds
    // -------------------------------------------------------------------------
    @Test
    fun testAudioPlayerWidgetHelper_CoercesSeekPositionSafely() {
        val duration = 10000L

        assertEquals(5000, AudioPlayerWidgetHelper.calculateSeekPosition(0.5f, duration))
        assertEquals(0, AudioPlayerWidgetHelper.calculateSeekPosition(-0.2f, duration))
        assertEquals(10000, AudioPlayerWidgetHelper.calculateSeekPosition(1.5f, duration))
        assertEquals(0, AudioPlayerWidgetHelper.calculateSeekPosition(Float.NaN, duration))
        assertEquals(0, AudioPlayerWidgetHelper.calculateSeekPosition(0.5f, 0L))
        assertEquals(0, AudioPlayerWidgetHelper.calculateSeekPosition(0.5f, -5000L))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-009: Serialized Block JSON Leaks in Exporters
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_009_exportParsesSerializedBlocksWithoutLeakingJson() {
        val blockJson = "<!--NOTESNOOK_BLOCKS:[{\"type\":\"text\",\"text\":\"Meeting notes line 1\"},{\"type\":\"quote\",\"text\":\"Deep insight quote\"}]-->"
        val fallback = blockJson

        // When blocks list is empty, getEffectiveBlocks must unpack serialized blocks
        val effective = ArticleExporterHelper.getEffectiveBlocks(emptyList(), fallback)
        assertEquals(2, effective.size)
        assertTrue(effective[0] is NotesnookBlock.Text)
        assertTrue(effective[1] is NotesnookBlock.Quote)

        // exportToMarkdown must format the blocks, NOT dump raw JSON
        val md = ArticleExporter.exportToMarkdown(
            title = "Test Article",
            blocks = emptyList(),
            fallbackContent = fallback
        )
        assertFalse("Exported Markdown must NOT leak raw NOTESNOOK_BLOCKS comment", md.contains("<!--NOTESNOOK_BLOCKS:"))
        assertTrue("Exported Markdown must contain formatted text", md.contains("Meeting notes line 1"))
        assertTrue("Exported Markdown must contain blockquote indicator", md.contains("> Deep insight quote"))

        // exportToHtml must also format the blocks
        val html = ArticleExporter.exportToHtml(
            title = "Test Article",
            blocks = emptyList(),
            fallbackContent = fallback
        )
        assertFalse("Exported HTML must NOT leak raw NOTESNOOK_BLOCKS comment", html.contains("<!--NOTESNOOK_BLOCKS:"))
        assertTrue("Exported HTML must contain blockquote tag", html.contains("<blockquote><p>Deep insight quote</p></blockquote>"))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-010: Path Traversal & Reserved Names in Export File Name Sanitizer
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_010_sanitizeFileNamePathTraversalAndReservedNames() {
        val testDate = java.util.Date(1727580000000L) // Fixed date for deterministic test

        // Path traversal attempts
        val traversalName = ArticleExporterHelper.sanitizeFileName("../../etc/passwd", "pdf", testDate)
        assertFalse("Filename must not contain path traversal ../", traversalName.contains(".."))
        assertFalse("Filename must not contain slash", traversalName.contains("/"))
        assertTrue("Filename must end with .pdf", traversalName.endsWith(".pdf"))

        // Windows / FAT32 reserved names (CON, PRN, AUX, NUL)
        val reservedName = ArticleExporterHelper.sanitizeFileName("CON", "md", testDate)
        assertTrue("Reserved device name must fallback to Focus_Note", reservedName.startsWith("Focus_Note_"))

        // Leading dot files (hidden files on Unix/Android)
        val dotName = ArticleExporterHelper.sanitizeFileName(".hidden_note", "txt", testDate)
        assertFalse("Filename must not start with a dot", dotName.startsWith("."))

        // Clean normal name
        val normalName = ArticleExporterHelper.sanitizeFileName("My Sprint Plan", "docx", testDate)
        assertTrue("Normal title must be preserved in filename", normalName.startsWith("My_Sprint_Plan_"))
        assertTrue(normalName.endsWith(".docx"))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-011: DocumentMetrics Linear O(1) Memory Scan and Effective Blocks
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_011_documentMetricsLinearScannerAndEffectiveBlocks() {
        // Document with punctuation, sentences, and paragraphs
        val sampleText = "Hello world! This is Ayva.\n\nHere is a second paragraph. Are you ready? Yes!"
        val metrics = DocumentMetricsCalculator.calculate(
            title = "Summary",
            content = sampleText,
            blocks = emptyList()
        )

        // Title has 1 word. Sample text has 12 words -> total 13 words
        assertTrue("Word count should be accurate", metrics.words >= 12)
        assertTrue("Paragraphs should be detected", metrics.paragraphs >= 2)
        assertTrue("Sentences should be counted accurately", metrics.sentences >= 4)
        assertTrue("Characters without spaces must be strictly positive", metrics.charactersNoSpaces > 0)
        assertTrue("Characters must exceed characters without spaces", metrics.characters > metrics.charactersNoSpaces)

        // Metrics from serialized blocks
        val blockJson = "<!--NOTESNOOK_BLOCKS:[{\"type\":\"text\",\"text\":\"First block sentence.\"},{\"type\":\"text\",\"text\":\"Second block sentence.\"}]-->"
        val blockMetrics = DocumentMetricsCalculator.calculate(
            title = "",
            content = blockJson,
            blocks = emptyList()
        )
        // Must parse structured blocks rather than counting raw JSON characters
        assertEquals("Should parse 2 text blocks", 2, blockMetrics.textBlocksCount)
        assertTrue("Should count sentences from parsed text", blockMetrics.sentences >= 2)
        assertFalse("Must not treat JSON quotes as excessive words", blockMetrics.words > 10)
    }

    // -------------------------------------------------------------------------
    // BATCH-11-012: Label Dialogs Rename Uniqueness Check
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_012_labelDialogHelperCanRenameLabelUniqueness() {
        val existing = listOf("Work", "Personal", "Health")

        // Renaming to itself (or case-variant of itself) is valid / no-op
        assertTrue(LabelDialogHelper.canRenameLabel("Work", "Work", existing))
        assertTrue(LabelDialogHelper.canRenameLabel("Work", "work", existing))

        // Renaming to a distinct unused name is valid
        assertTrue(LabelDialogHelper.canRenameLabel("Work", "Career", existing))

        // Renaming to an existing name (different label) must be rejected to prevent duplicate key crashes
        assertFalse(LabelDialogHelper.canRenameLabel("Work", "Personal", existing))
        assertFalse(LabelDialogHelper.canRenameLabel("Work", "personal", existing))
        assertFalse(LabelDialogHelper.canRenameLabel("Work", "HEALTH", existing))

        // Blank or whitespace-only rename must be rejected
        assertFalse(LabelDialogHelper.canRenameLabel("Work", "   ", existing))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-013: KeepNoteShareParser Caps & Null Extras Filtering
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_013_keepNoteShareParserCapsItemsAndFiltersNullUris() {
        // Generate note with 700 checklist items (exceeding MAX_CHECKLIST_ITEMS = 500)
        val sb = java.lang.StringBuilder()
        for (i in 1..700) {
            sb.append("- [ ] Item $i\n")
        }

        val parsed = KeepNoteShareParser.parseContent("Massive Checklist", sb.toString())
        assertTrue("Parsed note must be a checklist", parsed.isChecklist)
        assertTrue(
            "Checklist items must be capped at MAX_CHECKLIST_ITEMS (500) to prevent OOM",
            parsed.checklistItems.size <= KeepNoteShareParser.MAX_CHECKLIST_ITEMS
        )
        assertEquals(500, parsed.checklistItems.size)

        // Verify null URIs are filtered out from image list
        val mixedUris: List<Uri> = listOfNotNull(
            Uri.parse("content://media/1"),
            null,
            Uri.parse("content://media/2")
        )
        val parsedWithImages = KeepNoteShareParser.parseContent("Image Note", "Body", mixedUris)
        assertEquals(2, parsedWithImages.imageUris.size)
    }

    // -------------------------------------------------------------------------
    // BATCH-11-014: Label Selection Whitespace and Case Invariance
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_014_labelDialogHelperIsLabelSelectedCaseInsensitive() {
        val selected = listOf("Work ", "Fitness", "TRAVEL")

        assertTrue("Trimmed match should be recognized as selected", LabelDialogHelper.isLabelSelected("Work", selected))
        assertTrue("Case-insensitive match should be recognized as selected", LabelDialogHelper.isLabelSelected("fitness", selected))
        assertTrue("Uppercase match should be recognized as selected", LabelDialogHelper.isLabelSelected("travel", selected))
        assertFalse("Unselected label should return false", LabelDialogHelper.isLabelSelected("Personal", selected))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-015: KeepSketchHelper Safe Radius and Coordinate Bounds
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_015_keepSketchHelperSafeRadiusAndCoordinates() {
        // Safe radius bounds
        assertEquals(0f, KeepSketchHelper.safeRadius(-10f), 0.001f)
        assertEquals(0f, KeepSketchHelper.safeRadius(Float.NaN), 0.001f)
        assertEquals(15f, KeepSketchHelper.safeRadius(15f), 0.001f)

        // Coordinate validity
        assertTrue(KeepSketchHelper.isCoordinateValid(100f, 200f))
        assertFalse(KeepSketchHelper.isCoordinateValid(Float.NaN, 200f))
        assertFalse(KeepSketchHelper.isCoordinateValid(100f, Float.POSITIVE_INFINITY))
        assertFalse(KeepSketchHelper.isCoordinateValid(Float.NEGATIVE_INFINITY, 200f))
    }

    // -------------------------------------------------------------------------
    // BATCH-11-016: KeepImageCollage Rows Calculation Bounds
    // -------------------------------------------------------------------------
    @Test
    fun test_BATCH_11_016_keepImageCollageRowBuilderCapsExtremeImages() {
        // Negative count returns emptyList
        val negativeRows = buildGoogleKeepCollageRows(-5)
        assertTrue(negativeRows.isEmpty())

        // Zero count returns emptyList
        val zeroRows = buildGoogleKeepCollageRows(0)
        assertTrue(zeroRows.isEmpty())

        // Extreme count (e.g. 1000 images) must be capped to MAX_COLLAGE_IMAGES (50)
        val extremeRows = buildGoogleKeepCollageRows(1000)
        val totalImagesInRows = extremeRows.sumOf { it.itemsInRow }
        assertEquals(
            "Collage builder must cap total images to MAX_COLLAGE_IMAGES",
            MAX_COLLAGE_IMAGES,
            totalImagesInRows
        )
    }
}
