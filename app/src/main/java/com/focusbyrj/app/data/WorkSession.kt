package com.focusbyrj.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** An explicit work commitment, independent of a task's deadline and estimated effort. */
data class WorkSession(val id: String = UUID.randomUUID().toString(), val start: Long, val minutes: Int) {
    val end: Long get() { require(start <= Long.MAX_VALUE - minutes * 60_000L) { "The work interval exceeds the supported time range" }; return start + minutes * 60_000L }
    fun validate() {
        require(id.length in 1..80 && minutes in 1..1440)
        require(start <= Long.MAX_VALUE - minutes * 60_000L && end > start)
    }
    fun json() = JSONObject().put("id", id).put("start", start).put("minutes", minutes)
    companion object {
        fun read(j: JSONObject) = WorkSession(j.getString("id"), j.getLong("start"), j.getInt("minutes")).also { it.validate() }
        fun readList(a: JSONArray?): List<WorkSession> {
            if (a == null) return emptyList()
            require(a.length() <= 100)
            return (0 until a.length()).map { read(a.getJSONObject(it)) }.also(::validateList)
        }
        fun validateList(sessions: List<WorkSession>) {
            require(sessions.size <= 100 && sessions.map { it.id }.distinct().size == sessions.size)
            sessions.forEach { it.validate() }
            sessions.sortedBy { it.start }.zipWithNext().forEach { (a, b) -> require(a.end <= b.start) { "Work sessions overlap" } }
        }
    }
}
