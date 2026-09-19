package com.focusbyrj.app.data.drill

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

    @Query("UPDATE drill_sessions SET isClaimed = 1 WHERE sessionId = :sessionId")
    suspend fun markClaimed(sessionId: String)

    @Query("DELETE FROM drill_sessions")
    suspend fun deleteAllSessions()
}

@Database(entities = [DrillSessionEntity::class], version = 1, exportSchema = false)
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
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}

object DrillSessionRepository {
    // In-memory hot cache for instantaneous 0ms session lookups without JSON parsing
    private val memoryCache = ConcurrentHashMap<String, DrillSummary>()
    private var database: DrillDatabase? = null

    fun init(context: Context) {
        if (database == null) {
            database = DrillDatabase.getDatabase(context)
        }
    }

    fun saveSummary(summary: DrillSummary, context: Context? = null) {
        memoryCache[summary.sessionId] = summary
        val ctx = context
        if (database == null && ctx != null) {
            init(ctx)
        }
        database?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entity = DrillSessionEntity(
                        sessionId = summary.sessionId,
                        title = summary.title,
                        totalQuestions = summary.totalQuestions,
                        correctCount = summary.correctCount,
                        timeSpentSeconds = summary.timeSpentSeconds,
                        xpEarned = summary.xpEarned,
                        isBlitz = summary.isBlitz,
                        isClaimed = summary.isClaimed,
                        summaryJson = summary.toJson()
                    )
                    db.drillSessionDao().insertSession(entity)
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun getSummary(sessionId: String): DrillSummary? {
        memoryCache[sessionId]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val entity = database?.drillSessionDao()?.getSessionById(sessionId)
                if (entity != null) {
                    val parsed = DrillSummary.fromJson(entity.summaryJson)
                    memoryCache[sessionId] = parsed
                    parsed
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun markClaimed(sessionId: String) {
        memoryCache[sessionId]?.let { existing ->
            memoryCache[sessionId] = existing.copy(isClaimed = true)
        }
        database?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.drillSessionDao().markClaimed(sessionId)
                } catch (_: Exception) {}
            }
        }
    }
}
