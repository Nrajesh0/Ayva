package com.focusbyrj.app.ui.screens

import org.json.JSONObject

internal object RestoreReviewScope {
    fun describe(manifest: JSONObject): List<String> = buildList {
        fun count(key: String) = manifest.optJSONArray(key)?.length() ?: 0
        add("${count("habits")} habits · ${count("habitLogs")} progress records")
        add("${count("schedules")} routines · ${count("restrictions")} app boundaries")
        add("${count("mediaManifest")} attachments · ${count("chatMessages")} chat messages")
        add("${count("drillSessions")} completed drill results")
        if (
            manifest.has("vocabulary") ||
                manifest.has("learnedIdioms") ||
                manifest.has("learnedOws")
        )
            add("Vocabulary learning progress and bookmarks")
        val prefs = manifest.optJSONObject("preferences") ?: JSONObject()
        val groups =
            listOf(
                "Profile and rewards" to listOf("focus_economy_prefs", "aptitude_economy_prefs"),
                "Assistant preferences" to
                    listOf("ayva_personalization", "bubble_prefs", "ayva_alert_color_prefs"),
                "Saved templates and routines" to listOf("productivity_library"),
                "App and reminder settings" to listOf("focus_prefs", "focus_app_prefs"),
            )
        for ((label, keys) in groups) if (keys.any(prefs::has))
            add("$label: backed-up values apply")
    }
}
