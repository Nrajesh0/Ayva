package com.focusbyrj.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Idiom::class, Ows::class], version = 2, exportSchema = false)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE idioms ADD COLUMN learned_at INTEGER") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE ows ADD COLUMN learned_at INTEGER") } catch (_: Exception) {}
            }
        }
    }
}
