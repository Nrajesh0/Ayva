package com.focusbyrj.app.data

import org.json.JSONObject

/** Shared wire format for scheduled and requested vocabulary briefing cards. */
internal object VocabBriefPayload {
    fun encode(idiom: Idiom?, ows: Ows?, revisionIdiom: Idiom?, revisionOws: Ows?): JSONObject =
        JSONObject().apply {
            idiom?.let { put("idiom", it.toBriefJson()) }
            ows?.let { put("ows", it.toBriefJson()) }
            revisionIdiom?.let { put("rev_idiom", it.toBriefJson()) }
            revisionOws?.let { put("rev_ows", it.toBriefJson()) }
        }

    private fun Idiom.toBriefJson() =
        JSONObject().apply {
            put("id", id ?: -1)
            put("idiom", idiom)
            put("meaning", meaning)
        }

    private fun Ows.toBriefJson() =
        JSONObject().apply {
            put("id", id ?: -1)
            put("term", term)
            put("definition", definition)
        }
}
