package com.focusbyrj.app.util.backup

import org.json.JSONArray
import org.json.JSONObject

/** Interactive history must never resolve a source ID against unrelated destination data. */
internal object RestoreImportPolicy {
    fun vocabularyHistory(raw: String?): String? {
        if (raw == null) return null
        return try {
            val history = JSONObject(raw)
            for (kind in listOf("idiom", "ows", "rev_idiom", "rev_ows")) {
                history.optJSONObject(kind)?.remove("id")
            }
            history.toString()
        } catch (_: org.json.JSONException) {
            null
        }
    }

    private val literalId = Regex("""\bid\s*:\s*\d+""", RegexOption.IGNORE_CASE)

    fun remapTalkHistory(
        raw: String?,
        tasks: Map<Long, Long>,
        habits: Map<Long, Long>,
        schedules: Map<Long, Long>,
        warnings: MutableList<String>
    ): String? {
        if (raw == null) return null
        val removedLink = "Some chat card links were removed because their records were not in the backup."
        return try {
            val root = JSONObject(raw)
            fun remapReference(container: JSONObject?, key: String) {
                val reference = container?.optJSONObject(key) ?: return
                val id = reference.optLong("id")
                val mapped = when (reference.optString("kind")) {
                    "task" -> tasks[id]
                    "habit" -> habits[id]
                    else -> null
                }
                if (mapped == null || mapped <= 0) {
                    container.remove(key)
                    warnings += removedLink
                } else {
                    reference.put("id", mapped)
                }
            }

            val presentation = root.optJSONObject("presentation")
            remapReference(presentation, "reference")
            remapReference(presentation, "replyTarget")
            remapReference(presentation?.optJSONObject("reply"), "reference")
            presentation?.optJSONObject("receipt")?.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) remapReference(items.optJSONObject(i), "reference")
            }
            presentation?.optJSONObject("taskList")?.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val row = items.getJSONObject(i)
                    val id = tasks[row.optLong("id")]?.takeIf { it > 0 } ?: 0L
                    if (id == 0L) warnings += removedLink
                    row.put("id", id)
                }
            }
            root.optJSONArray("actions")?.let { actions ->
                val output = JSONArray()
                for (i in 0 until actions.length()) {
                    val action = actions.optJSONObject(i) ?: continue
                    when (action.optString("type")) {
                        "routine_toggle" -> {
                            val id = schedules[action.optLong("scheduleId")]
                            if (id == null || id !in 1..Int.MAX_VALUE.toLong()) {
                                warnings += removedLink
                                continue
                            }
                            action.put("scheduleId", id)
                        }
                        "ask_query" -> {
                            val query = action.optString("query").trim()
                            if (literalId.containsMatchIn(query) || query.startsWith("/ayva ") ||
                                query.startsWith("/plan apply revision ")) {
                                warnings += removedLink
                                continue
                            }
                        }
                    }
                    output.put(action)
                }
                root.put("actions", output)
            }
            root.toString()
        } catch (_: org.json.JSONException) {
            warnings += "An unreadable chat card was restored without interactive links."
            null
        }
    }

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
