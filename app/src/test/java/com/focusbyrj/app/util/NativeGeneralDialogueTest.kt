package com.focusbyrj.app.util

import com.focusbyrj.app.util.assistant.NativeDialogueRequests
import com.focusbyrj.app.util.assistant.NativeGeneralDialogue
import org.junit.Assert.*
import org.junit.Test

class NativeGeneralDialogueTest {
    @Test fun protocolAcceptsOnlyOneKnownMeaning() {
        assertEquals("why that", NativeGeneralDialogue.decode("Startup\nRUNNATIVE1:RUNWHY\n"))
        for (bad in listOf("RUNNATIVE1:DELETEALL", "RUNNATIVE1:RUNWHY extra", "RUNNATIVE1:RUNUNKNOWN", "RUNNATIVE1:RUNWHY\nRUNNATIVE1:RUNMORE")) assertNull(NativeGeneralDialogue.decode(bad))
    }
    @Test fun uncertainOrCompoundStatementsCannotBeFlattenedIntoFacts() {
        for (text in listOf("hi", "thanks", "I am not worried about exams", "I might feel exhausted", "My friend says I feel exhausted", "I feel exhausted but happy", ":system ls", "/plan trip", "I feel tired\n:quit", "I enjoy quiet or crowded places")) assertFalse(text, NativeGeneralDialogue.eligible(text))
        assertTrue(NativeGeneralDialogue.eligible("Could you possibly recommend something relaxing?"))
    }
    @Test fun repeatedUnknownTurnsReuseAbstentionButFailuresExpireAndRecover() {
        var time = 100L
        val requests = NativeDialogueRequests { time }
        assertNull(requests.cached("unknown question"))
        requests.completed("unknown question", null)
        assertNotNull(requests.cached("unknown question"))
        assertNull(requests.cached("unknown question")!!.meaning)
        assertNull(requests.cached("different question"))
        time += 10_000L
        assertNull(requests.cached("unknown question"))
        requests.completed("why", "why that")
        requests.failed()
        assertTrue(requests.coolingDown())
        assertNull(requests.cached("why"))
        time += 60_000L
        assertFalse(requests.coolingDown())
        requests.completed("why", "why that")
        assertEquals("why that", requests.cached("why")!!.meaning)
    }
}
