package com.focusbyrj.app.ui.screens.chat

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusbyrj.app.util.assistant.AssistantChatState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatLifecycleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hiddenChatStopsProjectionReadsAndResumesImmediately() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        var reads = 0
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                rememberChatStateProjection(Unit, 0) {
                    reads++
                    AssistantChatState(expiresAt = System.currentTimeMillis() + 60_000L)
                }
            }
        }
        compose.waitForIdle()
        assertTrue(reads > 0)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        val stoppedReads = reads
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(stoppedReads, reads)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertTrue(reads > stoppedReads)
    }
}
