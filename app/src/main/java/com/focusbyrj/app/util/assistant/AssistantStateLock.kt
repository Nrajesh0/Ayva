package com.focusbyrj.app.util.assistant

import kotlinx.coroutines.sync.Mutex

/** Serialize chat turns with full-data backup/restore; neither may observe a half-restored plan. */
internal object AssistantStateLock {
    val mutex = Mutex()
}
