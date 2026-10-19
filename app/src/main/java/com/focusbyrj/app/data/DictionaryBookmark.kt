package com.focusbyrj.app.data

import androidx.room.*

/** Links an imported source sense to the existing native vocabulary learning/bookmark row. */
@Entity(tableName = "dictionary_bookmarks")
data class DictionaryBookmark(@PrimaryKey val senseId: String, val wordKey: String, val owsId: Int)

@Dao
interface DictionaryBookmarkDao {
    @Query("SELECT * FROM dictionary_bookmarks WHERE senseId = :id")
    suspend fun get(id: String): DictionaryBookmark?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: DictionaryBookmark)
}
