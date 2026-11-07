package com.focusbyrj.app.data.drill

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(tableName = "drill_sessions")
data class DrillSessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val timeSpentSeconds: Long,
    val xpEarned: Int,
    val isBlitz: Boolean,
    val isClaimed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val summaryJson: String
)

@Dao
interface DrillSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DrillSessionEntity)

    @Query("SELECT * FROM drill_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): DrillSessionEntity?

    @Query("SELECT * FROM drill_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<DrillSessionEntity>>

    @Query("SELECT * FROM drill_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessionsSync(): List<DrillSessionEntity>

    @Query("SELECT * FROM drill_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<DrillSessionEntity>

    @Query("DELETE FROM drill_sessions")
    suspend fun deleteAllSessions()
}

@Database(entities = [DrillSessionEntity::class], version = 1, exportSchema = true)
abstract class DrillDatabase : RoomDatabase() {
    abstract fun drillSessionDao(): DrillSessionDao

    companion object {
        @Volatile
        private var INSTANCE: DrillDatabase? = null

        fun getDatabase(context: Context): DrillDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DrillDatabase::class.java,
                    "drill_sessions.db"
                )
                .openHelperFactory(com.focusbyrj.app.data.SecureRoomFactory.create(context))
                    .build()
                .also { INSTANCE = it }
            }
        }
    }
}

/** Cache entries are published only after Room acknowledges a durable write. */
object DrillSessionRepository {
    private const val MAX_CACHE_ENTRIES = 50
    private val memoryCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, DrillSummary>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DrillSummary>?) = size > MAX_CACHE_ENTRIES
        })
    private var database: DrillDatabase? = null
    private var appContext: Context? = null
    private val settlementMutex = kotlinx.coroutines.sync.Mutex()
    private val _saveError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val saveError: kotlinx.coroutines.flow.StateFlow<String?> = _saveError

    @Synchronized fun init(context: Context) {
        appContext = context.applicationContext
        if (database == null) database = DrillDatabase.getDatabase(context)
    }
    fun invalidateCache() { memoryCache.clear() }
    private fun db() = checkNotNull(database) { "Drill storage is not ready" }

    suspend fun saveSummary(summary: DrillSummary, context: Context? = null) = withContext(Dispatchers.IO) {
        if (context != null) init(context)
        val existing = db().drillSessionDao().getSessionById(summary.sessionId)
        val effective = if (existing?.isClaimed == true) summary.copy(isClaimed = true) else summary
        db().drillSessionDao().insertSession(DrillSessionEntity(
            sessionId = effective.sessionId, title = effective.title, totalQuestions = effective.totalQuestions,
            correctCount = effective.correctCount, timeSpentSeconds = effective.timeSpentSeconds,
            xpEarned = effective.xpEarned, isBlitz = effective.isBlitz, isClaimed = effective.isClaimed,
            timestamp = existing?.timestamp ?: System.currentTimeMillis(), summaryJson = effective.toJson()))
        memoryCache[effective.sessionId] = effective
        _saveError.value = null
    }

    suspend fun getSummary(sessionId: String): DrillSummary? = withContext(Dispatchers.IO) {
        memoryCache[sessionId] ?: db().drillSessionDao().getSessionById(sessionId)?.let { entity ->
            DrillSummary.fromJson(entity.summaryJson).copy(isClaimed = entity.isClaimed).also { memoryCache[sessionId] = it }
        }
    }

    /** One durable identity guards both reward settlement and summary persistence. */
    suspend fun <T> settleOnce(sessionId: String, existing: (DrillSummary) -> T, create: suspend () -> T): T {
        settlementMutex.lock()
        try {
            val context = checkNotNull(appContext) { "Drill storage is not ready" }
            val result = com.focusbyrj.app.util.backup.RestoreJournal.mutateDrill(context, sessionId) {
                runCatching {
                    val saved = db().drillSessionDao().getSessionById(sessionId)
                    if (saved != null) existing(DrillSummary.fromJson(saved.summaryJson).copy(isClaimed = saved.isClaimed)) else create()
                }
            }
            return result.getOrElse { error ->
                invalidateCache()
                _saveError.value = "Result could not be saved. Your drill is still open; try Finish again."
                throw error
            }
        } finally { settlementMutex.unlock() }
    }

    suspend fun markClaimed(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updated = db().withTransaction {
                val stored = requireNotNull(db().drillSessionDao().getSessionById(sessionId)) { "Drill result is unavailable" }
                val summary = DrillSummary.fromJson(stored.summaryJson).copy(isClaimed = true)
                db().drillSessionDao().insertSession(stored.copy(isClaimed = true, summaryJson = summary.toJson()))
                summary
            }
            memoryCache[sessionId] = updated
            _saveError.value = null
        }.onFailure { _saveError.value = "Could not save completion. Please try again." }
    }
}
