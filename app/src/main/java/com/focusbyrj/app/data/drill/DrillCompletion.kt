package com.focusbyrj.app.data.drill

import org.json.JSONObject

/** Persist the acknowledgement before updating the chat copy or showing success. */
internal object DrillCompletion {
    suspend fun claim(summaryJson: String, fallbackSessionId: String): String {
        val summary = JSONObject(summaryJson)
        val sessionId = summary.optString("sessionId", fallbackSessionId)
        DrillSessionRepository.markClaimed(sessionId).getOrThrow()
        summary.put("isClaimed", true)
        return summary.toString()
    }
}
