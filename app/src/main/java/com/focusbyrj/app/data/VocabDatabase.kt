package com.focusbyrj.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Idiom::class, Ows::class, DictionaryBookmark::class], version = 3, exportSchema = false)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun dictionaryBookmarkDao(): DictionaryBookmarkDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `dictionary_bookmarks` (`senseId` TEXT NOT NULL, `wordKey` TEXT NOT NULL, `owsId` INTEGER NOT NULL, PRIMARY KEY(`senseId`))")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE idioms ADD COLUMN learned_at INTEGER") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE ows ADD COLUMN learned_at INTEGER") } catch (_: Exception) {}
            }
        }
    }
}
