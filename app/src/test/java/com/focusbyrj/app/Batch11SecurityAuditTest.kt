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
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.ArticleTocHelper
import com.focusbyrj.app.ui.screens.notes.AudioPlayerWidgetHelper
import com.focusbyrj.app.ui.screens.notes.KeepNoteCardHelper
import com.focusbyrj.app.ui.screens.notes.KeepNoteShareParser
import com.focusbyrj.app.ui.screens.notes.KeepSketchHelper
import com.focusbyrj.app.ui.screens.notes.LabelDialogHelper
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.screens.notes.NotesnookUrlValidator
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
}
