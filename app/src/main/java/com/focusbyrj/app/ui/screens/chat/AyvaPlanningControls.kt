package com.focusbyrj.app.ui.screens.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.json.JSONObject

/** Existing planning cards now accept answers in the same chat composer. */
@Composable
internal fun AyvaPlanningControls(payload: String?, onQuery: ((String) -> Unit)?) {
    val plan = remember(payload) { runCatching { JSONObject(payload ?: "{}").optJSONObject("presentation")?.optJSONObject("plan") }.getOrNull() } ?: return
    if (onQuery != null && plan.optString("question") != "review")
        Text("Reply in chat to continue this plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
