package com.focusbyrj.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/** Copy public lexical content into a newly created encrypted database, never user state. */
internal object VocabSeed {
    fun install(context: Context, destination: SupportSQLiteDatabase) {
        val asset = File.createTempFile("public-vocabulary-", ".db", context.cacheDir)
        try {
            context.assets.open("vocab.db").use { input -> asset.outputStream().use { input.copyTo(it) } }
            SQLiteDatabase.openDatabase(asset.path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
                val tables = mapOf(
                    "idioms" to listOf("id", "sn", "idiom", "meaning", "repetition_ssc", "repetition_other", "is_top_200"),
                    "ows" to listOf("id", "sn", "term", "part_of_speech", "definition", "repetition_ssc", "repetition_other", "is_top_200", "is_spelling_pyq", "has_syno_anto"))
                tables.forEach { (table, columns) ->
                    val names = columns + listOf("is_mastered", "is_bookmarked", "learned_at")
                    destination.compileStatement("INSERT INTO $table (${names.joinToString(",")}) VALUES (${names.joinToString(",") { "?" }})").use { statement ->
                        source.query(table, columns.toTypedArray(), null, null, null, null, null).use { cursor ->
                            check(cursor.count > 0) { "Bundled vocabulary is empty" }
                            while (cursor.moveToNext()) {
                                statement.clearBindings()
                                columns.indices.forEach { i ->
                                    if (cursor.isNull(i)) statement.bindNull(i + 1)
                                    else if (cursor.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER) statement.bindLong(i + 1, cursor.getLong(i))
                                    else statement.bindString(i + 1, cursor.getString(i))
                                }
                                statement.bindLong(columns.size + 1, 0); statement.bindLong(columns.size + 2, 0)
                                statement.bindNull(columns.size + 3)
                                check(statement.executeInsert() != -1L) { "Vocabulary initialization failed" }
                            }
                        }
                    }
                }
            }
        } finally { asset.delete() }
    }
}
