package com.focusbyrj.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update

@Dao
interface VocabDao {
    @Query("SELECT * FROM idioms WHERE learned_at IS NULL AND is_mastered = 0 ORDER BY is_top_200 DESC, repetition_ssc DESC LIMIT :limit")
    suspend fun getUnlearnedIdioms(limit: Int): List<Idiom>

    @Query("SELECT * FROM ows WHERE learned_at IS NULL AND is_mastered = 0 ORDER BY is_top_200 DESC, repetition_ssc DESC LIMIT :limit")
    suspend fun getUnlearnedOws(limit: Int): List<Ows>

    @Query("SELECT * FROM idioms WHERE learned_at IS NOT NULL ORDER BY learned_at DESC")
    suspend fun getAllLearnedIdioms(): List<Idiom>

    @Query("SELECT * FROM ows WHERE learned_at IS NOT NULL ORDER BY learned_at DESC")
    suspend fun getAllLearnedOws(): List<Ows>

    // Spaced repetition review queries (Leitner queue: oldest unreviewed word first, unmastered prioritized)
    @Query("SELECT * FROM idioms WHERE learned_at IS NOT NULL AND is_mastered = 0 AND (:excludeId IS NULL OR id != :excludeId) ORDER BY learned_at ASC LIMIT :limit")
    suspend fun getRevisionIdioms(limit: Int, excludeId: Int? = null): List<Idiom>

    @Query("SELECT * FROM ows WHERE learned_at IS NOT NULL AND is_mastered = 0 AND (:excludeId IS NULL OR id != :excludeId) ORDER BY learned_at ASC LIMIT :limit")
    suspend fun getRevisionOws(limit: Int, excludeId: Int? = null): List<Ows>

    @Query("SELECT * FROM idioms WHERE learned_at IS NOT NULL AND (:excludeId IS NULL OR id != :excludeId) ORDER BY learned_at ASC LIMIT :limit")
    suspend fun getAllRevisionIdiomsFallback(limit: Int, excludeId: Int? = null): List<Idiom>

    @Query("SELECT * FROM ows WHERE learned_at IS NOT NULL AND (:excludeId IS NULL OR id != :excludeId) ORDER BY learned_at ASC LIMIT :limit")
    suspend fun getAllRevisionOwsFallback(limit: Int, excludeId: Int? = null): List<Ows>

    // Quiz queries: prioritize unmastered, supplement with mastered
    @Query("SELECT * FROM idioms WHERE learned_at IS NOT NULL AND is_mastered = 0 ORDER BY learned_at ASC LIMIT :limit")
    suspend fun getUnmasteredLearnedIdioms(limit: Int): List<Idiom>

    @Query("SELECT * FROM ows WHERE learned_at IS NOT NULL AND is_mastered = 0 ORDER BY learned_at ASC LIMIT :limit")
    suspend fun getUnmasteredLearnedOws(limit: Int): List<Ows>

    @Query("SELECT * FROM idioms WHERE learned_at IS NOT NULL AND is_mastered = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getMasteredLearnedIdioms(limit: Int): List<Idiom>

    @Query("SELECT * FROM ows WHERE learned_at IS NOT NULL AND is_mastered = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getMasteredLearnedOws(limit: Int): List<Ows>

    // Direct state updates
    @Query("UPDATE idioms SET learned_at = :timestamp WHERE id = :id")
    suspend fun setIdiomLearned(id: Int, timestamp: Long)

    @Query("UPDATE ows SET learned_at = :timestamp WHERE id = :id")
    suspend fun setOwsLearned(id: Int, timestamp: Long)

    @Query("UPDATE idioms SET is_mastered = :mastered, learned_at = :timestamp WHERE id = :id")
    suspend fun setIdiomMastery(id: Int, mastered: Int, timestamp: Long)

    @Query("UPDATE ows SET is_mastered = :mastered, learned_at = :timestamp WHERE id = :id")
    suspend fun setOwsMastery(id: Int, mastered: Int, timestamp: Long)

    @Query("UPDATE idioms SET is_bookmarked = :bookmarked WHERE id = :id")
    suspend fun setIdiomBookmarked(id: Int, bookmarked: Int)

    @Query("UPDATE ows SET is_bookmarked = :bookmarked WHERE id = :id")
    suspend fun setOwsBookmarked(id: Int, bookmarked: Int)

    @Query("SELECT * FROM idioms WHERE id = :id LIMIT 1")
    suspend fun getIdiomById(id: Int): Idiom?

    @Query("SELECT * FROM ows WHERE id = :id LIMIT 1")
    suspend fun getOwsById(id: Int): Ows?

    // Stats
    @Query("SELECT COUNT(*) FROM idioms WHERE learned_at IS NOT NULL")
    suspend fun getLearnedIdiomCount(): Int

    @Query("SELECT COUNT(*) FROM ows WHERE learned_at IS NOT NULL")
    suspend fun getLearnedOwsCount(): Int

    @Query("SELECT COUNT(*) FROM idioms WHERE is_mastered = 1")
    suspend fun getMasteredIdiomCount(): Int

    @Query("SELECT COUNT(*) FROM ows WHERE is_mastered = 1")
    suspend fun getMasteredOwsCount(): Int

    @Update
    suspend fun updateIdiom(idiom: Idiom)

    @Update
    suspend fun updateOws(ows: Ows)

    @Query("SELECT * FROM idioms WHERE learned_at IS NOT NULL ORDER BY learned_at DESC LIMIT 1")
    suspend fun getLastLearnedIdiom(): Idiom?

    @Query("SELECT * FROM ows WHERE learned_at IS NOT NULL ORDER BY learned_at DESC LIMIT 1")
    suspend fun getLastLearnedOws(): Ows?
}
