package com.focusbyrj.app.ui.screens.chat

import android.app.Application
import com.focusbyrj.app.ui.screens.ChatMessage
import com.focusbyrj.app.util.PersistedChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ChatRecordLifecycleTest {
    @Test fun hiddenReplyRemainsUnviewedUntilTheChatIsVisible() {
        val reply = ChatMessage("reply", "Finished", false)
        assertEquals(0L, reply.toStableChatRecord(null, visible = false).firstViewedTimestamp)
        assertTrue(reply.toStableChatRecord(null, visible = true).firstViewedTimestamp > 0L)
    }
    @Test fun repeatedProjectionDoesNotExtendAnExistingViewedTimestamp() {
        val ui = ChatMessage("viewed", "Read", false)
        val persisted = PersistedChatMessage("viewed", "Read", false, firstViewedTimestamp = 123L)
        assertEquals(123L, ui.toStableChatRecord(persisted, visible = true).firstViewedTimestamp)
        assertEquals(123L, ui.toStableChatRecord(persisted, visible = false).firstViewedTimestamp)
    }
}
