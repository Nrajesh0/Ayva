package com.focusbyrj.app.util.backup

import com.focusbyrj.app.data.*
import org.json.JSONArray
import org.json.JSONObject

/** Portable earned history; balances are backed up separately after the outbox is drained. */
internal object RewardArchive {
    fun encode(rows: List<EarnedReward>) =
        JSONArray().apply {
            rows.forEach { row ->
                check(row.state != "PENDING") {
                    "Pending reward delivery must finish before backup."
                }
                put(
                    JSONObject()
                        .put("id", row.id)
                        .put("kind", row.kind)
                        .put("sourceId", row.sourceId)
                        .put("period", row.period)
                        .put("ordinal", row.ordinal)
                        .put("xp", row.xp)
                        .put("gold", row.gold)
                        .put("state", row.state)
                        .put("snapshot", row.snapshot)
                        .put("endAt", row.endAt)
                )
            }
        }

    fun decode(array: JSONArray): List<EarnedReward> {
        val unique = hashSetOf<List<Any>>()
        val ids = hashSetOf<String>()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val row =
                EarnedReward(
                    o.getString("id"),
                    o.getString("kind"),
                    o.getLong("sourceId"),
                    o.getString("period"),
                    o.getInt("ordinal"),
                    o.getInt("xp"),
                    o.getInt("gold"),
                    o.getString("state"),
                    o.getString("snapshot"),
                    o.getLong("endAt"),
                )
            require(
                row.id.length in 1..120 &&
                    ids.add(row.id) &&
                    row.sourceId > 0 &&
                    row.kind in setOf("HABIT_LOG", "HABIT_GOAL", "ROUTINE") &&
                    row.ordinal >= 0 &&
                    row.xp in 0..100 &&
                    row.gold in 0..25 &&
                    row.period.length in 1..80 &&
                    row.state in setOf("APPLIED", "RUNNING", "CANCELLED") &&
                    unique.add(listOf(row.kind, row.sourceId, row.period, row.ordinal))
            ) {
                "Invalid earned reward history"
            }
            if (row.kind == "ROUTINE") {
                require(row.endAt > 0)
                BackupArchiveLimits.checkEmbeddedJson(row.snapshot)
                ScheduleJson.decode(JSONObject(row.snapshot))
            } else require(row.snapshot.isEmpty() && row.state == "APPLIED")
            row
        }
    }

    suspend fun restore(
        db: FocusDatabase,
        array: JSONArray?,
        replace: Boolean,
        habits: Map<Long, Long>,
        schedules: Map<Long, Long>,
    ) {
        val dao = db.earnedRewardDao()
        if (replace) dao.deleteAll()
        if (array != null) {
            for (row in decode(array)) {
                val mapped =
                    (if (row.kind == "ROUTINE") schedules else habits)[row.sourceId] ?: continue
                dao.insert(
                    row.copy(
                        id = if (replace) row.id else java.util.UUID.randomUUID().toString(),
                        sourceId = mapped,
                        state = if (row.state == "RUNNING") "CANCELLED" else "APPLIED",
                    )
                )
            }
        } else {
            // No history of what old releases already awarded. Preserve balances and settle those
            // days.
            for (log in db.habitDao().getAllLogsSync()) {
                if (log.habitId in habits.values)
                    db.habitDao().settleLog(log, settleWholeDay = true)
            }
        }
    }
}
