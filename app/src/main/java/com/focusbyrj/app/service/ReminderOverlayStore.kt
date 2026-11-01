package com.focusbyrj.app.service

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only identities and occurrence information are persisted; task/habit text stays in the DB. */
internal data class PendingOverlayReminder(
    val kind: String,
    val id: Long,
    val dueDate: Long = 0L,
    val reminderDate: String = "",
    val openReschedule: Boolean = false
) {
    val key: String get() = "$kind:$id"
}

internal class ReminderOverlayStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "pending-reminder-overlays.json"))

    fun read(): List<PendingOverlayReminder> = try {
        val data = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        (0 until data.length()).mapNotNull { index ->
            runCatching {
                val item = data.getJSONObject(index)
                val kind = item.getString("kind")
                val id = item.getLong("id")
                require(kind in setOf("task", "habit") && id > 0)
                PendingOverlayReminder(kind, id, item.optLong("dueDate"),
                    item.optString("reminderDate"), item.optBoolean("openReschedule"))
            }.getOrNull()
        }.distinctBy { it.key }
    } catch (_: java.io.FileNotFoundException) {
        emptyList()
    } catch (error: Exception) {
        Log.e("ReminderOverlayStore", "Cannot read pending reminders", error)
        emptyList()
    }

    fun write(items: List<PendingOverlayReminder>) {
        val data = JSONArray()
        items.forEach { item ->
            data.put(JSONObject().put("kind", item.kind).put("id", item.id)
                .put("dueDate", item.dueDate).put("reminderDate", item.reminderDate)
                .put("openReschedule", item.openReschedule))
        }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(data.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { file.failWrite(it) }
            Log.e("ReminderOverlayStore", "Cannot save pending reminders", error)
        }
    }
}
