package com.focusbyrj.app.util.backup

import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.core.util.AtomicFile
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.util.assistant.AssistantPlanningArchive
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Device-bound, authenticated before-state. It is retained until all stores commit. */
internal object RestoreJournal {
    private const val NAME = "restore_before_state.enc"
    private const val MARKER = "restore_committed"
    internal var checkpointHook: ((String) -> Unit)? = null
    fun checkpoint(phase: String) { checkpointHook?.invoke(phase) }
    private fun databases(app: FocusApplication): List<RoomDatabase> = listOf(
        NoteDatabase.getInstance(app), app.database,
        com.focusbyrj.app.data.drill.DrillDatabase.getDatabase(app), app.vocabDatabase,
        com.focusbyrj.app.data.chat.AyvaChatDatabase.getDatabase(app))
    private fun journal(context: Context) = File(context.filesDir, NAME)
    private fun marker(context: Context) = AtomicFile(File(context.filesDir, MARKER))

    suspend fun <T> consistent(context: Context, block: suspend () -> T): T = MaintenanceGate.exclusively {
        recoverLocked(context.applicationContext as FocusApplication)
        block()
    }

    suspend fun <T> mutate(context: Context, block: suspend () -> Result<T>): Result<T> = try {
        mutateLocked(context, block)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Result.failure(error)
    }

    private suspend fun <T> mutateLocked(context: Context, block: suspend () -> Result<T>): Result<T> = MaintenanceGate.exclusively {
        val app = context.applicationContext as FocusApplication
        recoverLocked(app)
        val before = capture(app)
        EncryptedSnapshotFile.write(journal(app), before.toString())
        check(EncryptedSnapshotFile.read(journal(app)) == before.toString()) { "Restore recovery journal verification failed" }
        try {
            checkpoint("prepared")
            val result = block()
            if (result.isFailure) {
                withContext(NonCancellable) { recoverLocked(app) }
            } else {
                com.focusbyrj.app.data.note.NotesStorageVersion.replaced()
                checkpoint("before_commit")
                val output = marker(app).startWrite()
                try { output.write(before.getString("id").toByteArray()); output.fd.sync(); marker(app).finishWrite(output) }
                catch (error: Throwable) { marker(app).failWrite(output); throw error }
                checkpoint("committed")
                cleanup(app)
            }
            result
        } catch (error: Exception) {
            try { withContext(NonCancellable) { recoverLocked(app) } }
            catch (recovery: Exception) {
                MaintenanceGate.requireRecovery()
                throw IllegalStateException("Restore recovery is pending. Restart Run; your encrypted recovery journal has been retained.", recovery)
            }
            throw error
        } catch (error: Throwable) {
            // VM termination / fatal errors leave the durable journal for startup recovery.
            MaintenanceGate.requireRecovery()
            throw error
        }
    }

    /** Called before startup managers/workers and before any new maintenance operation. */
    suspend fun recover(context: Context) {
        val file = journal(context)
        if (!file.exists() && !File(file.path + ".bak").exists()) return
        MaintenanceGate.exclusively { recoverLocked(context.applicationContext as FocusApplication) }
    }

    private fun cleanup(context: Context) {
        val file = journal(context)
        AtomicFile(file).delete()
        check(listOf(file, File(file.path + ".bak"), File(file.path + ".new")).none { it.exists() }) {
            "Recovery journal cleanup is pending; commit marker retained"
        }
        // Delete journal before marker: a kill here must never turn a committed restore into rollback.
        marker(context).delete()
        MaintenanceGate.recovered()
    }

    private suspend fun recoverLocked(app: FocusApplication) {
        val file = journal(app)
        if (!file.exists() && !File(file.path + ".bak").exists()) { marker(app).delete(); MaintenanceGate.recovered(); return }
        MaintenanceGate.requireRecovery()
        val root = JSONObject(EncryptedSnapshotFile.read(file))
        require(root.getInt("version") == 1)
        val committed = runCatching { marker(app).openRead().use { it.readBytes().decodeToString() } }.getOrNull()
        if (committed == root.getString("id")) { cleanup(app); return }
        val dbs = databases(app)
        val interruptedTasks = app.database.taskDao().getAllTasksList()
        val interruptedHabits = app.database.habitDao().getAllHabitsSync()
        val stored = root.getJSONArray("databases")
        require(stored.length() == dbs.size)
        // The journal is authenticated local data, never an imported SQL program.
        dbs.forEachIndexed { index, room ->
            val snapshot = stored.getJSONObject(index)
            room.withTransaction {
                val db = room.openHelper.writableDatabase
                require(db.version == snapshot.getInt("schema")) { "Recovery schema changed; preserving journal" }
                db.execSQL("PRAGMA defer_foreign_keys = ON")
                val tables = snapshot.getJSONArray("tables")
                val known = tableNames(room).toSet()
                for (i in 0 until tables.length()) {
                    val table = tables.getJSONObject(i).getString("name")
                    require(table in known)
                    db.execSQL("DELETE FROM ${quote(table)}")
                }
                for (i in 0 until tables.length()) {
                    val table = tables.getJSONObject(i)
                    val columns = table.getJSONArray("columns")
                    val rows = table.getJSONArray("rows")
                    val names = (0 until columns.length()).joinToString(",") { quote(columns.getString(it)) }
                    val sql = "INSERT INTO ${quote(table.getString("name"))} ($names) VALUES (${(0 until columns.length()).joinToString(",") { "?" }})"
                    for (r in 0 until rows.length()) {
                        val row = rows.getJSONArray(r)
                        val args = Array<Any?>(columns.length()) { c -> decode(row.getJSONArray(c)) }
                        db.execSQL(sql, args)
                    }
                }
            }
        }
        val prefs = root.getJSONObject("preferences")
        prefs.keys().forEach { name ->
            require(!name.contains('/') && !name.contains('\\'))
            val editor = app.getSharedPreferences(name, 0).edit().clear()
            val values = prefs.getJSONObject(name)
            values.keys().forEach { key ->
                val typed = values.getJSONArray(key)
                when (typed.getString(0)) {
                    "b" -> editor.putBoolean(key, typed.getBoolean(1))
                    "i" -> editor.putInt(key, typed.getInt(1))
                    "l" -> editor.putLong(key, typed.getLong(1))
                    "f" -> editor.putFloat(key, typed.getDouble(1).toFloat())
                    "s" -> editor.putString(key, typed.getString(1))
                    "set" -> editor.putStringSet(key, typed.getJSONArray(1).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                }
            }
            check(editor.commit()) { "Preference recovery failed" }
        }
        val files = root.getJSONObject("assistant")
        val bytes = files.keys().asSequence().associateWith { name ->
            if (files.isNull(name)) null else Base64.decode(files.getString(name), Base64.NO_WRAP)
        }
        AssistantPlanningArchive.recover(app, AssistantPlanningArchive.Recovery(bytes))
        ArchiveVaultSecurity.lockVault()
        NotesViewModel.latestNotesCache.clear()
        // Derived schedules and caches must agree with the recovered stores as well.
        runCatching {
            interruptedTasks.forEach { com.focusbyrj.app.util.TaskReminderHelper.cancelReminder(app, it) }
            interruptedHabits.forEach { com.focusbyrj.app.util.HabitAlarmScheduler.cancelHabitReminder(app, it.id) }
            app.database.taskDao().getAllTasksList().filter { !it.isCompleted && !it.isTrashed }.forEach {
                com.focusbyrj.app.util.TaskReminderHelper.scheduleReminder(app, it)
            }
            app.database.habitDao().getAllHabitsSync().forEach { com.focusbyrj.app.util.HabitAlarmScheduler.scheduleHabitReminder(app, it) }
            com.focusbyrj.app.util.WorkSessionReminders.reconcileAll(app)
        }.onFailure { runCatching { com.focusbyrj.app.util.WorkSessionReminders.retry(app) } }
        runCatching { com.focusbyrj.app.util.FocusEconomyManager.init(app) }
        runCatching { com.focusbyrj.app.util.FocusStatsManager.init(app) }
        runCatching { com.focusbyrj.app.util.BubbleChatManager.init(app) }
        runCatching { com.focusbyrj.app.util.AptitudeManager.init(app) }
        runCatching { com.focusbyrj.app.util.DailyQuestManager.init(app) }
        checkpoint("recovered")
        cleanup(app)
    }

    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    private fun tableNames(room: RoomDatabase): List<String> = room.openHelper.writableDatabase.query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private suspend fun capture(app: FocusApplication): JSONObject {
        val data = JSONArray()
        for (room in databases(app)) {
            room.withTransaction {
                val tables = JSONArray()
                tableNames(room).forEach { name ->
                    room.openHelper.writableDatabase.query("SELECT * FROM ${quote(name)}").use { cursor ->
                        val rows = JSONArray()
                        while (cursor.moveToNext()) {
                            val row = JSONArray()
                            for (c in 0 until cursor.columnCount) row.put(when (cursor.getType(c)) {
                                Cursor.FIELD_TYPE_NULL -> JSONArray().put("n")
                                Cursor.FIELD_TYPE_INTEGER -> JSONArray().put("l").put(cursor.getLong(c))
                                Cursor.FIELD_TYPE_FLOAT -> JSONArray().put("d").put(cursor.getDouble(c))
                                Cursor.FIELD_TYPE_BLOB -> JSONArray().put("x").put(Base64.encodeToString(cursor.getBlob(c), Base64.NO_WRAP))
                                else -> JSONArray().put("s").put(cursor.getString(c))
                            })
                            rows.put(row)
                        }
                        tables.put(JSONObject().put("name", name).put("columns", JSONArray(cursor.columnNames.toList())).put("rows", rows))
                    }
                }
                data.put(JSONObject().put("schema", room.openHelper.writableDatabase.version).put("tables", tables))
            }
        }
        val names = (File(app.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .filter { it.name.endsWith(".xml") }.map { it.name.removeSuffix(".xml") } +
            BackupRestoreManager.recoveryPreferenceNames()).distinct()
        val prefs = JSONObject()
        names.forEach { name ->
            val values = JSONObject()
            app.getSharedPreferences(name, 0).all.forEach { (key, value) ->
                val pair = when (value) {
                    is Boolean -> JSONArray().put("b").put(value)
                    is Int -> JSONArray().put("i").put(value)
                    is Long -> JSONArray().put("l").put(value)
                    is Float -> JSONArray().put("f").put(value)
                    is String -> JSONArray().put("s").put(value)
                    is Set<*> -> JSONArray().put("set").put(JSONArray(value.toList()))
                    else -> null
                }
                pair?.let { values.put(key, it) }
            }
            prefs.put(name, values)
        }
        val assistant = JSONObject()
        AssistantPlanningArchive.captureRecovery(app).bytes.forEach { (name, bytes) -> assistant.put(name, bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL) }
        return JSONObject().put("version", 1).put("id", UUID.randomUUID().toString())
            .put("databases", data).put("preferences", prefs).put("assistant", assistant)
    }
    private fun decode(value: JSONArray): Any? = when (value.getString(0)) {
        "n" -> null; "l" -> value.getLong(1); "d" -> value.getDouble(1)
        "x" -> Base64.decode(value.getString(1), Base64.NO_WRAP); else -> value.getString(1)
    }
}
