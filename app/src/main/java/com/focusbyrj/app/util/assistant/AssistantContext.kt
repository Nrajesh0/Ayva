package com.focusbyrj.app.util.assistant

/** Absolute authority lifetimes; unrelated chat activity cannot extend a reference or review. */
internal object AssistantContext {
    fun live(at: Long, now: Long = System.currentTimeMillis()) = at > 0 && now - at in 0..900_000L

    fun expire(state: AssistantSession, now: Long = System.currentTimeMillis()) {
        if (!live(state.referenceAt, now)) {
            state.taskId = null
            state.habitId = null
            state.selectedTaskIds = emptyList()
            state.featureState?.remove("routineRef")
            state.featureState?.remove("appSelection")
            // A slot with captured targets is not a fresh user selection.
            if (state.pending.any { it is AssistantAction.EditTasks || it is AssistantAction.EditHabit }) state.clearPending()
        }
        if (state.confirmationId != null && !live(state.confirmationAt, now)) state.clearPending()
    }
}
