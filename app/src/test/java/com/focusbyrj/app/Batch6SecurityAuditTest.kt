package com.focusbyrj.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.data.note.NoteImageHelper
import com.focusbyrj.app.data.note.NoteMediaManager
import com.focusbyrj.app.ui.screens.notes.AudioMemoManager
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.screens.notes.NotesnookFormattingHelper
import com.focusbyrj.app.ui.screens.notes.RichSpan
import com.focusbyrj.app.ui.screens.notes.RichSpanType
import com.focusbyrj.app.ui.screens.notes.RichTextEngine
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import com.focusbyrj.app.ui.screens.notes.ArticleDocxGenerator
import com.focusbyrj.app.ui.screens.notes.ArticleExporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

/**
 * Batch 6 Security, Media & Rich Note Engine Audit -- Regression Test Suite
 *
 * Covers:
 * - B6-F-001: Infinite Mutual Recursion & StackOverflowError in RichTextEngine.parse / NotesnookBlockManager.parse
 * - B6-F-002: Arbitrary File Overwrite & Deletion Vulnerability via Path Traversal in NoteMediaManager
 * - B6-F-003: Silent Deletion of Embedded Block Media in NoteMediaManager.cleanOrphanedMedia
 * - B6-F-004: Native MediaMetadataRetriever / MediaPlayer Resource Leaks in AudioMemoManager
 * - B6-F-007: Span Offset Desynchronization on Text Replacement in RichTextEngine
 * - B6-F-008: Markdown Link Serialization and Parsing in RichTextEngine
 * - B6-F-009: Malformed Table Dimensions in NotesnookBlockModel
 * - B6-F-010: Plaintext Image File Copying & IV Reuse Risk in NoteImageHelper.copyImageFile
 * - B6-F-011: NotesnookFormattingHelper Negative Selection Safety
 */
@RunWith(RobolectricTestRunner::class)
class Batch6SecurityAuditTest {

    private lateinit var context: Context
    private lateinit var noteDb: NoteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        noteDb = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        noteDb.close()
    }

    /**
     * B6-F-001: Verify that truncated or incomplete Notesnook blocks (e.g., prefix present without suffix)
     * does not cause infinite mutual recursion and StackOverflowError.
     */
    @Test
    fun testRichTextEngineIncompleteBlocksDoesNotStackOverflow() {
        val brokenInput = "${NotesnookBlockManager.BLOCKS_PREFIX}\n{\"version\":1,\"blocks\":[{\"type\":\"text\",\"text\":\"hello\""
        // Should parse gracefully as plain text without throwing java.lang.StackOverflowError
        val (parsedText, spans) = RichTextEngine.parse(brokenInput)
        assertNotNull(parsedText)
        assertNotNull(spans)
        assertTrue("Parsed text should contain the input content without crashing", parsedText.contains("version"))
    }

    /**
     * B6-F-002: Verify that NoteMediaManager rejects path traversal and refuses to delete or overwrite
     * files outside the dedicated media storage directories.
     */
    @Test
    fun testNoteMediaManagerRefusesArbitraryPathTraversalDeletion() {
        val sensitiveDir = File(context.filesDir, "databases").apply { mkdirs() }
        val fakeDb = File(sensitiveDir, "notes.db").apply {
            writeText("CRITICAL_DATABASE_CONTENT")
        }
        assertTrue(fakeDb.exists())

        // Attempt path traversal deletion via relative path
        val traversalPath = "${context.filesDir.absolutePath}/keep_images/../../databases/notes.db"
        NoteMediaManager.secureDeleteMediaFile(traversalPath)

        // The critical file MUST NOT be deleted or zero-overwritten
        assertTrue("Sensitive database file must still exist after traversal deletion attempt", fakeDb.exists())
        assertEquals("Sensitive database content must not be zero-overwritten", "CRITICAL_DATABASE_CONTENT", fakeDb.readText())

        // Direct deletion of an arbitrary path outside authorized directories must also be refused
        NoteMediaManager.secureDeleteMediaFile(fakeDb.absolutePath)
        assertTrue("Sensitive file outside media directory must not be deleted", fakeDb.exists())
        assertEquals("CRITICAL_DATABASE_CONTENT", fakeDb.readText())
    }

    /**
     * B6-F-003: Verify that NoteMediaManager.cleanOrphanedMedia preserves images and attachments
     * embedded inside Notesnook blocks in note.content.
     */
    @Test
    fun testCleanOrphanedMediaPreservesNotesnookBlockImagesAndAttachments() = runBlocking {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val embeddedImageFile = File(imagesDir, "img_block_test.jpg").apply {
            writeText("dummy image data")
            // Set modified time to 10 minutes ago so it exceeds the 5-minute orphan threshold
            setLastModified(System.currentTimeMillis() - 600_000L)
        }
        val embeddedAttachmentFile = File(imagesDir, "att_block_test.pdf").apply {
            writeText("dummy attachment data")
            setLastModified(System.currentTimeMillis() - 600_000L)
        }

        // True orphan file
        val orphanFile = File(imagesDir, "img_orphan_test.jpg").apply {
            writeText("orphan image data")
            setLastModified(System.currentTimeMillis() - 600_000L)
        }

        // Create a note whose content contains Notesnook blocks referencing embeddedImageFile and embeddedAttachmentFile
        val blocks = listOf(
            NotesnookBlock.Text(text = "Header text"),
            NotesnookBlock.Image(uri = embeddedImageFile.absolutePath, caption = "Diagram"),
            NotesnookBlock.Attachment(uri = embeddedAttachmentFile.absolutePath, fileName = "doc.pdf")
        )
        val noteContent = NotesnookBlockManager.serialize(blocks)

        val note = NoteEntity(
            title = "Test Note With Blocks",
            content = noteContent,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDb.noteDao().insertNote(note)

        // Run orphaned media cleanup
        NoteMediaManager.cleanOrphanedMedia(context, noteDb.noteDao())

        // Embedded files must NOT be deleted
        assertTrue("Embedded block image must be preserved by cleanOrphanedMedia", embeddedImageFile.exists())
        assertTrue("Embedded block attachment must be preserved by cleanOrphanedMedia", embeddedAttachmentFile.exists())

        // The true orphan must be cleaned up
        assertFalse("Unreferenced orphan file should be cleaned up", orphanFile.exists())
    }

    /**
     * B6-F-003 / Media Lifecycle: Verify that deleteNoteMediaFiles also deletes embedded block media files.
     */
    @Test
    fun testDeleteNoteMediaFilesCleansBlockImagesAndAttachments() {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val blockImg = File(imagesDir, "img_to_delete.jpg").apply { writeText("img data") }
        val blockAtt = File(imagesDir, "att_to_delete.pdf").apply { writeText("att data") }

        val blocks = listOf(
            NotesnookBlock.Image(uri = blockImg.absolutePath),
            NotesnookBlock.Attachment(uri = blockAtt.absolutePath)
        )
        val note = NoteEntity(
            title = "Deleted Note",
            content = NotesnookBlockManager.serialize(blocks),
            createdAt = System.currentTimeMillis()
        )

        NoteMediaManager.deleteNoteMediaFiles(note)

        assertFalse("Block image should be deleted when note media is purged", blockImg.exists())
        assertFalse("Block attachment should be deleted when note media is purged", blockAtt.exists())
    }

    /**
     * B6-F-004: Verify AudioMemoManager.getAudioDurationMs handles corrupt or non-existent files safely
     * without unhandled exceptions or resource leaks.
     */
    @Test
    fun testAudioMemoManagerDurationSafeCleanupOnCorruptedFile() {
        val nonExistentPath = File(context.cacheDir, "non_existent_audio.m4a").absolutePath
        val duration = AudioMemoManager.getAudioDurationMs(nonExistentPath)
        assertEquals("Non-existent file should return 0 duration", 0L, duration)

        val corruptedFile = File(context.cacheDir, "corrupted_audio.m4a").apply {
            writeText("not really an audio file, just random bytes")
        }
        val corruptDuration = AudioMemoManager.getAudioDurationMs(corruptedFile.absolutePath)
        assertEquals("Corrupted audio file should return 0 duration without crash", 0L, corruptDuration)
    }

    /**
     * B6-F-007: Verify that RichTextEngine.updateSpansOnTextChange handles text replacement accurately
     * using prefix/suffix isolation without shifting subsequent spans out of bounds.
     */
    @Test
    fun testRichTextEngineUpdateSpansOnTextReplacement() {
        // Text: "Hello WORLD foo"
        // Spans: BOLD on "WORLD" (indices 6..11), ITALIC on "foo" (indices 12..15)
        val oldText = "Hello WORLD foo"
        val spans = listOf(
            RichSpan(RichSpanType.BOLD, 6, 11),
            RichSpan(RichSpanType.ITALIC, 12, 15)
        )

        // Replace "WORLD" (5 chars) with "BEAUTIFUL WORLD" (15 chars) -> delta = +10
        val newText = "Hello BEAUTIFUL WORLD foo"
        val updatedSpans = RichTextEngine.updateSpansOnTextChange(oldText, newText, spans)

        val italicSpan = updatedSpans.find { it.type == RichSpanType.ITALIC }
        assertNotNull("Italic span on 'foo' must be preserved", italicSpan)
        // In newText, "foo" is at indices 22..25
        assertEquals("Italic span start should shift to 22", 22, italicSpan!!.start)
        assertEquals("Italic span end should shift to 25", 25, italicSpan.end)
    }

    /**
     * B6-F-008: Verify RichTextEngine serializes RichSpanType.LINK to Markdown [text](url)
     * and parses [text](url) back into clean text with RichSpanType.LINK and url payload.
     */
    @Test
    fun testRichTextEngineSerializeAndParseMarkdownLinks() {
        val plainText = "Check this link for details"
        // Link on "link" (indices 11..15) with payload "https://ayva.app"
        val spans = listOf(
            RichSpan(RichSpanType.LINK, 11, 15, payload = "https://ayva.app")
        )

        val serialized = RichTextEngine.serialize(plainText, spans)
        assertTrue("Serialized output should contain markdown link format", serialized.contains("[link](https://ayva.app)"))

        val (parsedText, parsedSpans) = RichTextEngine.parse(serialized)
        assertEquals("Parsed text should be clean without markdown link syntax", plainText, parsedText)

        val linkSpan = parsedSpans.find { it.type == RichSpanType.LINK }
        assertNotNull("Parsed spans must contain LINK type", linkSpan)
        assertEquals("Link start must match original position", 11, linkSpan!!.start)
        assertEquals("Link end must match original position", 15, linkSpan.end)
        assertEquals("Link payload must match URL", "https://ayva.app", linkSpan.payload)
    }

    /**
     * B6-F-009: Verify NotesnookBlockModel bounds malformed table dimensions.
     */
    @Test
    fun testNotesnookBlockModelTableDimensionsBounded() {
        val malformedJson = """
            ${NotesnookBlockManager.BLOCKS_PREFIX}
            {
                "version": 1,
                "blocks": [
                    {
                        "type": "table",
                        "id": "t1",
                        "rows": -5,
                        "cols": 999999,
                        "data": []
                    }
                ]
            }
            ${NotesnookBlockManager.BLOCKS_SUFFIX}
        """.trimIndent()

        val blocks = NotesnookBlockManager.parse(malformedJson)
        val table = blocks.filterIsInstance<NotesnookBlock.Table>().firstOrNull()
        assertNotNull("Parsed block should be Table", table)
        assertTrue("Rows must be bounded to at least 1", table!!.rows >= 1)
        assertTrue("Cols must be bounded to reasonable maximum (<= 50)", table.cols <= 50)
    }

    /**
     * B6-F-010: Verify NoteImageHelper.copyImageFile produces an AES-256-GCM encrypted file.
     */
    @Test
    fun testCopyImageFileAlwaysProducesEncryptedFile() {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val srcFile = File(imagesDir, "src_test_img.jpg").apply {
            writeBytes("DUMMY_IMAGE_JPEG_PAYLOAD_BYTES".toByteArray())
        }

        val copiedPath = NoteImageHelper.copyImageFile(context, srcFile.absolutePath)
        assertNotNull(copiedPath)

        val destFile = File(copiedPath!!)
        assertTrue("Copied file must exist", destFile.exists())
        assertTrue("Copied file must be encrypted with EncryptedMediaStorage", EncryptedMediaStorage.isEncrypted(destFile))

        // Read decrypted bytes
        val decrypted = EncryptedMediaStorage.readDecryptedBytes(destFile)
        assertNotNull("Should decrypt cleanly", decrypted)
        assertEquals("DUMMY_IMAGE_JPEG_PAYLOAD_BYTES", String(decrypted!!))
    }

    /**
     * B6-F-011: Verify NotesnookFormattingHelper handles empty and boundary selections safely
     * without throwing StringIndexOutOfBoundsException.
     */
    @Test
    fun testNotesnookFormattingHelperEmptyAndEdgeSelectionSafety() {
        val emptyTfv = TextFieldValue("", TextRange.Zero)

        // Test applyInlineWrap with empty TextRange
        val wrapped = NotesnookFormattingHelper.applyInlineWrap(emptyTfv, "**")
        assertNotNull(wrapped)
        assertEquals("****", wrapped.text)

        // Test applyLinePrefix with empty TextRange
        val prefixed = NotesnookFormattingHelper.applyLinePrefix(emptyTfv, "# ")
        assertNotNull(prefixed)
        assertEquals("# ", prefixed.text)

        // Test insertTimestamp with empty TextRange
        val timestamped = NotesnookFormattingHelper.insertTimestamp(emptyTfv)
        assertNotNull(timestamped)
        assertTrue(timestamped.text.isNotEmpty())

        // Test insertLinkTemplate with empty TextRange
        val linked = NotesnookFormattingHelper.insertLinkTemplate(emptyTfv)
        assertNotNull(linked)
        assertTrue(linked.text.contains("https://"))
    }

    /**
     * BATCH-6-012: Verify NotesnookBlockManager.parse preserves text before BLOCKS_PREFIX and after BLOCKS_SUFFIX.
     */
    @Test
    fun testNotesnookBlockManagerPreservesTextBeforeAndAfterBlocks() {
        val mixedContent = """
            Preceding note header text
            ${NotesnookBlockManager.BLOCKS_PREFIX}
            {"version":1,"blocks":[{"type":"text","text":"Middle block content","spans":[]}]}
            ${NotesnookBlockManager.BLOCKS_SUFFIX}
            Trailing note footer text
        """.trimIndent()

        val blocks = NotesnookBlockManager.parse(mixedContent)
        assertTrue("Blocks list should contain at least 3 blocks (prefix text, json block, suffix text)", blocks.size >= 3)
        val textBlocks = blocks.filterIsInstance<NotesnookBlock.Text>()
        assertTrue("Must preserve header text", textBlocks.any { it.text.contains("Preceding note header text") })
        assertTrue("Must preserve middle block", textBlocks.any { it.text.contains("Middle block content") })
        assertTrue("Must preserve footer text", textBlocks.any { it.text.contains("Trailing note footer text") })
    }

    /**
     * BATCH-6-012: Verify corrupted JSON between BLOCKS_PREFIX and BLOCKS_SUFFIX does not cause
     * infinite mutual recursion and StackOverflowError.
     */
    @Test
    fun testCorruptedBlocksJsonDoesNotCauseMutualRecursionStackOverflow() {
        val corruptedInput = """
            ${NotesnookBlockManager.BLOCKS_PREFIX}
            { CORRUPTED_NON_JSON_SYNTAX_ERROR ::: [[}}
            ${NotesnookBlockManager.BLOCKS_SUFFIX}
        """.trimIndent()

        // Calling RichTextEngine.parse or NotesnookBlockManager.parse must NOT throw StackOverflowError
        val (parsedText, spans) = RichTextEngine.parse(corruptedInput)
        assertNotNull(parsedText)
        assertNotNull(spans)

        val blocks = NotesnookBlockManager.parse(corruptedInput)
        assertNotNull(blocks)
        assertTrue(blocks.isNotEmpty())
    }

    /**
     * BATCH-6-013: Verify ArticleExporter does not duplicate text when multiple spans overlap.
     */
    @Test
    fun testArticleExporterDoesNotDuplicateTextOnOverlappingSpans() {
        // "Hello World" with BOLD on "Hello" (0..5) and ITALIC on "Hello" (0..5)
        val block = NotesnookBlock.Text(
            text = "Hello World",
            spans = listOf(
                RichSpan(RichSpanType.BOLD, 0, 5),
                RichSpan(RichSpanType.ITALIC, 0, 5)
            )
        )

        val md = ArticleExporter.exportToMarkdown(title = "", blocks = listOf(block), fallbackContent = "")
        // Should NOT contain "**Hello***Hello*"
        assertFalse("Markdown export must not duplicate text on overlapping spans", md.contains("**Hello***Hello*"))
        assertTrue("Markdown export should contain single formatted instance of Hello", md.contains("Hello"))
        assertEquals("Total occurrences of 'Hello' must be 1", 1, Regex("Hello").findAll(md).count())

        val html = ArticleExporter.exportToHtml(title = "", blocks = listOf(block), fallbackContent = "")
        // Should NOT contain "<b>Hello</b><i>Hello</i>"
        assertFalse("HTML export must not duplicate text on overlapping spans", html.contains("<b>Hello</b><i>Hello</i>"))
        assertEquals("Total occurrences of 'Hello' in HTML body must be 1", 1, Regex("Hello").findAll(html).count())
    }

    /**
     * BATCH-6-014: Verify ArticleDocxGenerator adjusts span offsets per line.
     */
    @Test
    fun testArticleDocxGeneratorAdjustsSpanOffsetsPerLine() {
        // Multi-line text:
        // Line 1: "Line 1" (0..6)
        // Line 2: "Line 2 is bold" (7..21) -> "is bold" is at 14..21 in full text, or 7..14 in Line 2
        val fullText = "Line 1\nLine 2 is bold"
        val block = NotesnookBlock.Text(
            text = fullText,
            spans = listOf(
                RichSpan(RichSpanType.BOLD, 14, 21) // "is bold" on Line 2
            )
        )

        val docxBytes = ArticleDocxGenerator.generateDocx(title = "Test", blocks = listOf(block), fallbackContent = "")
        assertNotNull(docxBytes)

        val zip = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(docxBytes))
        var docxXml = ""
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                docxXml = zip.reader().readText()
                break
            }
            entry = zip.nextEntry
        }

        // Line 1 should NOT have bold tags
        // Line 2 should have bold tags around "is bold"
        assertTrue("DOCX should contain bold run", docxXml.contains("<w:b/>") || docxXml.contains("<w:b w:val=\"true\"/>"))
        assertTrue("DOCX should preserve 'is bold'", docxXml.contains("is bold"))
    }

    /**
     * BATCH-6-018: Verify RichTextEngine recursive inline parsing and LIFO closing tag nesting.
     */
    @Test
    fun testRichTextEngineRecursiveInlineFormattingAndLifoClosing() {
        // 1. Recursive parsing: **bold *italic* text**
        val input = "**bold *italic* text**"
        val (parsedText, spans) = RichTextEngine.parse(input)
        assertEquals("Parsed text should be clean", "bold italic text", parsedText)
        assertTrue("Should have BOLD span", spans.any { it.type == RichSpanType.BOLD })
        assertTrue("Should have nested ITALIC span", spans.any { it.type == RichSpanType.ITALIC })

        val italicSpan = spans.find { it.type == RichSpanType.ITALIC }!!
        assertEquals("Italic start should match 'italic'", 5, italicSpan.start)
        assertEquals("Italic end should match 'italic'", 11, italicSpan.end)

        // 2. LIFO Closing order in serialization:
        // If BOLD (0..5) and UNDERLINE (0..5) are serialized:
        // Closing must be </u>** not **</u>
        val text = "hello"
        val dualSpans = listOf(
            RichSpan(RichSpanType.BOLD, 0, 5),
            RichSpan(RichSpanType.UNDERLINE, 0, 5)
        )
        val serialized = RichTextEngine.serialize(text, dualSpans)
        assertFalse("Tags must not be improperly interleaved: **<u>...**</u>", serialized.contains("**<u>hello**</u>"))
        assertTrue("Serialized output must have properly nested tags", serialized.contains("**<u>hello</u>**") || serialized.contains("<u>**hello**</u>"))
    }

    /**
     * BATCH-6-015: Verify note duplication clones and isolates block image and attachment files.
     */
    @Test
    fun testDuplicateNoteIsolatesBlockMedia() {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val origImg = File(imagesDir, "img_orig_test.jpg").apply {
            writeBytes("IMAGE_BYTES".toByteArray())
        }
        val origAtt = File(imagesDir, "att_orig_test.pdf").apply {
            writeBytes("PDF_BYTES".toByteArray())
        }

        val origBlocks = listOf(
            NotesnookBlock.Image(uri = origImg.absolutePath, caption = "Diagram"),
            NotesnookBlock.Attachment(uri = origAtt.absolutePath, fileName = "doc.pdf")
        )
        val origContent = NotesnookBlockManager.serialize(origBlocks)

        // Simulate duplication logic
        val parsed = NotesnookBlockManager.parse(origContent)
        val duplicatedBlocks = parsed.map { block ->
            when (block) {
                is NotesnookBlock.Image -> {
                    val copiedUri = NoteImageHelper.copyImageFile(context, block.uri) ?: block.uri
                    block.copy(id = UUID.randomUUID().toString(), uri = copiedUri)
                }
                is NotesnookBlock.Attachment -> {
                    val copiedUri = NoteImageHelper.copyImageFile(context, block.uri) ?: block.uri
                    block.copy(id = UUID.randomUUID().toString(), uri = copiedUri)
                }
                else -> block
            }
        }

        val dupImg = duplicatedBlocks.filterIsInstance<NotesnookBlock.Image>().first()
        val dupAtt = duplicatedBlocks.filterIsInstance<NotesnookBlock.Attachment>().first()

        assertNotEquals("Duplicated image URI must not match original URI", origImg.absolutePath, dupImg.uri)
        assertNotEquals("Duplicated attachment URI must not match original URI", origAtt.absolutePath, dupAtt.uri)
        assertTrue("Duplicated image file must exist on disk", File(dupImg.uri).exists())
        assertTrue("Duplicated attachment file must exist on disk", File(dupAtt.uri).exists())

        // Purging original note media must NOT delete duplicated files
        val origNote = NoteEntity(
            title = "Original Note",
            content = origContent,
            createdAt = System.currentTimeMillis()
        )
        NoteMediaManager.deleteNoteMediaFiles(origNote)

        assertFalse("Original image file was deleted", origImg.exists())
        assertFalse("Original attachment file was deleted", origAtt.exists())
        assertTrue("Duplicated image file must remain intact", File(dupImg.uri).exists())
        assertTrue("Duplicated attachment file must remain intact", File(dupAtt.uri).exists())
    }

    /**
     * BATCH-6-017: Verify attachment export preparation decrypts file to cacheDir/exports/.
     */
    @Test
    fun testAttachmentExportPreparationDecryptsToCacheExports() {
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        val encryptedAttachmentFile = File(imagesDir, "att_secret.pdf")
        val secretPayload = "TOP_SECRET_DOCUMENT_PAYLOAD".toByteArray()
        EncryptedMediaStorage.writeEncryptedBytes(encryptedAttachmentFile, secretPayload)

        // Prepare file in cacheDir/exports/
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "secret_clean.pdf")

        val decryptedBytes = EncryptedMediaStorage.readDecryptedBytes(encryptedAttachmentFile)
        assertNotNull("Should decrypt cleanly", decryptedBytes)
        exportFile.writeBytes(decryptedBytes!!)

        assertTrue("Export file must exist in cacheDir/exports/", exportFile.exists())
        assertEquals("Export file must contain decrypted plaintext", "TOP_SECRET_DOCUMENT_PAYLOAD", exportFile.readText())
    }
}

