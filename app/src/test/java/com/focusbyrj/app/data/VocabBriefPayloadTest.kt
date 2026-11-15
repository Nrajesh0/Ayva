package com.focusbyrj.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VocabBriefPayloadTest {
    @Test
    fun briefingPreservesNewAndRevisionEntriesAndOmitsUnavailableOnes() {
        val next = Idiom(id = 7, idiom = "A test idiom", meaning = "A test meaning")
        val revision = Ows(id = 8, term = "A revision term", definition = "A definition")
        val payload = VocabBriefPayload.encode(next, null, null, revision)
        assertEquals(2, payload.length())
        assertEquals(7, payload.getJSONObject("idiom").getInt("id"))
        assertEquals(next.meaning, payload.getJSONObject("idiom").getString("meaning"))
        assertEquals(revision.term, payload.getJSONObject("rev_ows").getString("term"))
        assertFalse(payload.has("ows"))
        assertFalse(payload.has("rev_idiom"))
        assertEquals(0, VocabBriefPayload.encode(null, null, null, null).length())
        assertEquals(
            -1,
            VocabBriefPayload.encode(next.copy(id = null), null, null, null)
                .getJSONObject("idiom")
                .getInt("id"),
        )
    }
}
