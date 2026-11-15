package com.focusbyrj.app.util.backup

import org.json.JSONArray
import org.json.JSONObject

/** Interactive history must never resolve a source ID against unrelated destination data. */
internal object RestoreImportPolicy {
    fun remapCards(raw: String?, ids: Map<Long, Long>, warnings: MutableList<String>): String? {
        if (raw == null) return null
        return try {
            val input = JSONArray(raw)
            val output = JSONArray()
            for (i in 0 until input.length()) {
                val row = input.getJSONObject(i)
                val mapped = ids[row.getLong("id")]
                if (mapped == null)
                    warnings +=
                        "Some chat card links were removed because their records were not in the backup."
                else output.put(JSONObject(row.toString()).put("id", mapped))
            }
            output.toString()
        } catch (_: Exception) {
            warnings += "An unreadable chat card was restored without interactive links."
            "[]"
        }
    }
}
