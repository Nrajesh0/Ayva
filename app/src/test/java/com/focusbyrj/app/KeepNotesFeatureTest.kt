package com.focusbyrj.app

import com.focusbyrj.app.data.note.ChecklistItem
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.KeepColorPalette
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeepNotesFeatureTest {

    @Test
    fun testChecklistSerializationAndDeserialization() {
        val items = listOf(
            ChecklistItem(id = "1", text = "Buy organic milk", isChecked = false),
            ChecklistItem(id = "2", text = "Call dentist", isChecked = true),
            ChecklistItem(id = "3", text = "Water houseplants", isChecked = false)
        )

        val json = ChecklistItem.listToJson(items)
        assertTrue(json.contains("Buy organic milk"))
        assertTrue(json.contains("Call dentist"))

        val parsed = ChecklistItem.listFromJson(json)
        assertEquals(3, parsed.size)
        assertEquals("Buy organic milk", parsed[0].text)
        assertFalse(parsed[0].isChecked)
        assertEquals("Call dentist", parsed[1].text)
        assertTrue(parsed[1].isChecked)
    }

    @Test
    fun testLabelsSerializationAndParsing() {
        val labels = listOf("Personal", "Work", "Urgent")
        val array = JSONArray()
        labels.forEach { array.put(it) }

        val note = NoteEntity(
            title = "Test note with labels",
            labelsJson = array.toString()
        )

        val retrievedLabels = note.getLabels()
        assertEquals(3, retrievedLabels.size)
        assertTrue(retrievedLabels.contains("Personal"))
        assertTrue(retrievedLabels.contains("Work"))
        assertTrue(retrievedLabels.contains("Urgent"))
    }

    @Test
    fun testImageUrisSerializationAndParsing() {
        val images = listOf("content://media/external/images/media/100", "content://media/external/images/media/101")
        val array = JSONArray()
        images.forEach { array.put(it) }

        val note = NoteEntity(
            title = "Note with images",
            imageUrisJson = array.toString()
        )

        val retrievedImages = note.getImageUris()
        assertEquals(2, retrievedImages.size)
        assertEquals("content://media/external/images/media/100", retrievedImages[0])
        assertEquals("content://media/external/images/media/101", retrievedImages[1])
    }

    @Test
    fun testKeepColorPalette() {
        val coral = KeepColorPalette.getColor("coral")
        assertEquals("coral", coral.key)
        assertEquals("Coral", coral.name)

        val defaultColor = KeepColorPalette.getColor("non_existent_key")
        assertEquals("default", defaultColor.key)
        assertEquals("Default", defaultColor.name)
    }

    @Test
    fun testKeepNoteShareParser_ChecklistWithSubject() {
        val rawSubject = "Weekend Groceries"
        val rawText = "☐ Milk 2L\n☑ Whole wheat bread\n☐ Cheddar cheese"

        val parsed = com.focusbyrj.app.ui.screens.notes.KeepNoteShareParser.parseContent(rawSubject, rawText)

        assertEquals("Weekend Groceries", parsed.title)
        assertTrue(parsed.isChecklist)
        assertEquals(3, parsed.checklistItems.size)
        assertEquals("Milk 2L", parsed.checklistItems[0].text)
        assertFalse(parsed.checklistItems[0].isChecked)
        assertEquals("Whole wheat bread", parsed.checklistItems[1].text)
        assertTrue(parsed.checklistItems[1].isChecked)
        assertEquals("Cheddar cheese", parsed.checklistItems[2].text)
        assertFalse(parsed.checklistItems[2].isChecked)
    }

    @Test
    fun testKeepNoteShareParser_ChecklistWithoutSubjectFirstLineHeading() {
        val rawSubject = null
        val rawText = "Trip Prep\n- [ ] Passport\n- [x] Flight Tickets\n- [ ] Currency exchange"

        val parsed = com.focusbyrj.app.ui.screens.notes.KeepNoteShareParser.parseContent(rawSubject, rawText)

        assertEquals("Trip Prep", parsed.title)
        assertTrue(parsed.isChecklist)
        assertEquals(3, parsed.checklistItems.size)
        assertEquals("Passport", parsed.checklistItems[0].text)
        assertFalse(parsed.checklistItems[0].isChecked)
        assertEquals("Flight Tickets", parsed.checklistItems[1].text)
        assertTrue(parsed.checklistItems[1].isChecked)
    }

    @Test
    fun testKeepNoteShareParser_PlainNoteWithImages() {
        val rawSubject = "Meeting Summary"
        val rawText = "Discussed Q4 roadmaps and deliverables.\nNext sync on Monday 10am."
        val fakeUri = android.net.Uri.parse("content://keep/attachments/12345")

        val parsed = com.focusbyrj.app.ui.screens.notes.KeepNoteShareParser.parseContent(rawSubject, rawText, listOf(fakeUri))

        assertEquals("Meeting Summary", parsed.title)
        assertFalse(parsed.isChecklist)
        assertTrue(parsed.content.contains("Discussed Q4 roadmaps"))
        assertEquals(1, parsed.imageUris.size)
        assertEquals(fakeUri, parsed.imageUris[0])
    }

    @Test
    fun testKeepNoteShareParser_SecurityValidation() {
        val parser = com.focusbyrj.app.ui.screens.notes.KeepNoteShareParser

        // 1. Valid URIs should be accepted
        val validContent = android.net.Uri.parse("content://media/external/images/media/12345")
        val validResource = android.net.Uri.parse("android.resource://com.other.app/drawable/icon")
        assertTrue(parser.isSafeShareUri(validContent))
        assertTrue(parser.isSafeShareUri(validResource))

        // 2. Unsafe schemes (file://, http://, javascript://) should be rejected
        val fileUri = android.net.Uri.parse("file:///data/data/com.focusbyrj.app/databases/keep_notes_encrypted.db")
        val httpUri = android.net.Uri.parse("http://malicious.com/exploit.jpg")
        val jsUri = android.net.Uri.parse("javascript:alert(1)")
        assertFalse(parser.isSafeShareUri(fileUri))
        assertFalse(parser.isSafeShareUri(httpUri))
        assertFalse(parser.isSafeShareUri(jsUri))

        // 3. Self-referential FileProvider URIs should be rejected (prevent internal reflection)
        val selfFileProviderUri = android.net.Uri.parse("content://com.focusbyrj.app.fileprovider/exports/secret.txt")
        assertFalse(parser.isSafeShareUri(selfFileProviderUri))

        // 4. Directory traversal in URI string should be rejected
        val traversalUri = android.net.Uri.parse("content://external/images/../../files/db")
        val encodedTraversalUri = android.net.Uri.parse("content://external/images/%2e%2e/db")
        assertFalse(parser.isSafeShareUri(traversalUri))
        assertFalse(parser.isSafeShareUri(encodedTraversalUri))

        // 5. Null or empty URI should be rejected
        assertFalse(parser.isSafeShareUri(null))
    }
}
