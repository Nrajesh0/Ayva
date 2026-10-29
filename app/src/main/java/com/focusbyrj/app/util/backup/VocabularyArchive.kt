package com.focusbyrj.app.util.backup

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import com.focusbyrj.app.data.VocabDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Word identity, not device-local numeric IDs, controls portable vocabulary recovery. */
internal object VocabularyArchive {
    fun export(database: VocabDatabase): JSONObject {
        val db = database.openHelper.writableDatabase
        fun rows(sql: String): JSONArray = db.query(sql).use { c ->
            JSONArray().apply { while (c.moveToNext()) put(JSONObject().apply {
                c.columnNames.forEachIndexed { i, name -> put(name, when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    else -> c.getString(i)
                }) }
            }) }
        }
        return JSONObject().put("version", 1)
            .put("idioms", rows("SELECT * FROM idioms WHERE learned_at IS NOT NULL OR is_bookmarked = 1 OR is_mastered = 1"))
            .put("ows", rows("SELECT * FROM ows WHERE learned_at IS NOT NULL OR is_bookmarked = 1 OR is_mastered = 1 OR id IN (SELECT owsId FROM dictionary_bookmarks)"))
            .put("links", rows("SELECT * FROM dictionary_bookmarks"))
    }
    fun validate(root: JSONObject) {
        require(root.getInt("version") == 1) { "Unsupported vocabulary archive" }
        val ids = mutableSetOf<Long>()
        listOf("idioms", "ows", "links").forEach { name -> require(root.getJSONArray(name).length() <= 100_000) }
        val idioms = root.getJSONArray("idioms")
        val idiomIds = mutableSetOf<Long>()
        for (i in 0 until idioms.length()) {
            val row = idioms.getJSONObject(i)
            require(idiomIds.add(row.getLong("id"))) { "Duplicate idiom ID" }
            row.getString("idiom"); row.getString("meaning")
        }
        val words = root.getJSONArray("ows")
        for (i in 0 until words.length()) {
            val row = words.getJSONObject(i)
            require(ids.add(row.getLong("id"))) { "Duplicate vocabulary ID" }
            row.getString("term"); row.getString("definition")
        }
        val senses = mutableSetOf<String>()
        val links = root.getJSONArray("links")
        for (i in 0 until links.length()) {
            val row = links.getJSONObject(i)
            require(senses.add(row.getString("senseId")) && row.getLong("owsId") in ids) { "Invalid dictionary sense link" }
            row.getString("wordKey")
        }
    }
    suspend fun restore(database: VocabDatabase, root: JSONObject, clean: Boolean) {
        validate(root)
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            if (clean) {
                db.execSQL("UPDATE idioms SET learned_at = NULL, is_bookmarked = 0, is_mastered = 0")
                db.execSQL("UPDATE ows SET learned_at = NULL, is_bookmarked = 0, is_mastered = 0")
                db.execSQL("DELETE FROM dictionary_bookmarks")
            }
            val idMap = mutableMapOf<Long, Long>()
            for (table in listOf("idioms", "ows")) {
                val first = if (table == "idioms") "idiom" else "term"
                val second = if (table == "idioms") "meaning" else "definition"
                val allowed = db.query("PRAGMA table_info($table)").use { c -> buildSet { while(c.moveToNext()) add(c.getString(1)) } }
                val rows = root.getJSONArray(table)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val partClause = if (table == "ows") " AND part_of_speech IS ?" else ""
                    val identity = mutableListOf<Any?>(row.getString(first), row.getString(second))
                    if (table == "ows") identity.add(if (row.isNull("part_of_speech")) null else row.getString("part_of_speech"))
                    val existing = db.query("SELECT id FROM $table WHERE $first = ? AND $second = ?$partClause ORDER BY id LIMIT 1",
                        identity.toTypedArray()).use { if(it.moveToFirst()) it.getLong(0) else null }
                    val values = ContentValues()
                    row.keys().forEach { key ->
                        if (key != "id" && key in allowed) {
                            if (row.isNull(key)) values.putNull(key)
                            else when (val value = row.get(key)) {
                                is Number -> values.put(key, value.toLong())
                                else -> values.put(key, value.toString())
                            }
                        }
                    }
                    val localId = if (existing == null) db.insert(table, 2, values).also { check(it > 0) }
                    else {
                        if (!clean) {
                            // Merge preserves local learning and bookmarks when the import is unset.
                            if (row.isNull("learned_at")) values.remove("learned_at")
                            if (row.optInt("is_mastered") == 0) values.remove("is_mastered")
                            if (row.optInt("is_bookmarked") == 0) values.remove("is_bookmarked")
                        }
                        db.update(table, 2, values, "id = ?", arrayOf(existing)); existing
                    }
                    if (table == "ows") idMap[row.getLong("id")] = localId
                }
            }
            val links = root.getJSONArray("links")
            for (i in 0 until links.length()) {
                val row = links.getJSONObject(i)
                val values = ContentValues().apply {
                    put("senseId", row.getString("senseId")); put("wordKey", row.getString("wordKey"))
                    put("owsId", checkNotNull(idMap[row.getLong("owsId")]))
                }
                db.insert("dictionary_bookmarks", 5, values)
            }
        }
    }
}
