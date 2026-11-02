package com.focusbyrj.app.ui.screens.chat

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.focusbyrj.app.util.assistant.AssistantChatState
import com.focusbyrj.app.ui.screens.toPersistedChatMessage
import kotlinx.coroutines.delay

/** UI-only projection: suspend reads when hidden and refresh immediately on return. */
@Composable
internal fun rememberChatStateProjection(
    refreshKey: Any?,
    referenceRevision: Int,
    readState: suspend () -> AssistantChatState
): MutableState<AssistantChatState> {
    val owner = LocalLifecycleOwner.current
    val latestRead by rememberUpdatedState(readState)
    val state = remember { mutableStateOf(AssistantChatState()) }
    LaunchedEffect(owner, refreshKey, referenceRevision) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.value = latestRead()
                val remaining = state.value.expiresAt - System.currentTimeMillis() + 1L
                delay(remaining.coerceIn(1L, 5_000L))
            }
        }
    }
    return state
}

/** Keep an already-viewed timestamp stable; hidden replies remain unviewed. */
internal fun com.focusbyrj.app.ui.screens.ChatMessage.toStableChatRecord(
    previous: com.focusbyrj.app.util.PersistedChatMessage?,
    visible: Boolean
): com.focusbyrj.app.util.PersistedChatMessage {
    val viewed = firstViewedTimestamp.takeIf { it > 0L } ?: previous?.firstViewedTimestamp ?: 0L
    return copy(firstViewedTimestamp = viewed).toPersistedChatMessage(markViewedIfActive = visible)
}
