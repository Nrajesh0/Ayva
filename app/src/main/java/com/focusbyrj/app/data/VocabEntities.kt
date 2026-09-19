package com.focusbyrj.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "idioms",
    indices = [
        Index(value = ["idiom"], name = "idx_idiom_name"),
        Index(value = ["is_top_200", "repetition_ssc"], name = "idx_idiom_priority", orders = [Index.Order.DESC, Index.Order.DESC])
    ]
)
data class Idiom(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val sn: Int? = null,
    val idiom: String,
    val meaning: String,
    @ColumnInfo(name = "repetition_ssc", defaultValue = "0") val repetitionSsc: Int? = 0,
    @ColumnInfo(name = "repetition_other", defaultValue = "0") val repetitionOther: Int? = 0,
    @ColumnInfo(name = "is_top_200", defaultValue = "0") val isTop200: Int? = 0,
    @ColumnInfo(name = "is_mastered", defaultValue = "0") val isMastered: Int? = 0,
    @ColumnInfo(name = "is_bookmarked", defaultValue = "0") val isBookmarked: Int? = 0,
    @ColumnInfo(name = "learned_at") val learnedAt: Long? = null
)

@Entity(
    tableName = "ows",
    indices = [
        Index(value = ["term"], name = "idx_ows_term"),
        Index(value = ["is_top_200", "repetition_ssc"], name = "idx_ows_priority", orders = [Index.Order.DESC, Index.Order.DESC])
    ]
)
data class Ows(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val sn: Int? = null,
    val term: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String? = null,
    val definition: String,
    @ColumnInfo(name = "repetition_ssc", defaultValue = "0") val repetitionSsc: Int? = 0,
    @ColumnInfo(name = "repetition_other", defaultValue = "0") val repetitionOther: Int? = 0,
    @ColumnInfo(name = "is_top_200", defaultValue = "0") val isTop200: Int? = 0,
    @ColumnInfo(name = "is_spelling_pyq", defaultValue = "0") val isSpellingPyq: Int? = 0,
    @ColumnInfo(name = "has_syno_anto", defaultValue = "0") val hasSynoAnto: Int? = 0,
    @ColumnInfo(name = "is_mastered", defaultValue = "0") val isMastered: Int? = 0,
    @ColumnInfo(name = "is_bookmarked", defaultValue = "0") val isBookmarked: Int? = 0,
    @ColumnInfo(name = "learned_at") val learnedAt: Long? = null
)
