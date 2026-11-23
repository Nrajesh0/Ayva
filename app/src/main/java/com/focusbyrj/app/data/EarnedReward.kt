package com.focusbyrj.app.data

import androidx.room.*

/**
 * Durable completion receipt and delivery outbox. No foreign key: deletion must not re-earn credit.
 */
@Entity(
    tableName = "earned_rewards",
    indices =
        [
            Index(value = ["kind", "sourceId", "period", "ordinal"], unique = true),
            Index(value = ["state"]),
        ],
)
data class EarnedReward(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val kind: String,
    val sourceId: Long,
    val period: String,
    val ordinal: Int = 0,
    val xp: Int = 0,
    val gold: Int = 0,
    val state: String = "PENDING",
    val snapshot: String = "",
    val endAt: Long = 0,
)

@Dao
interface EarnedRewardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: EarnedReward): Long

    @Query("SELECT * FROM earned_rewards WHERE state = 'PENDING' ORDER BY id")
    suspend fun pending(): List<EarnedReward>

    @Query("UPDATE earned_rewards SET state = 'APPLIED' WHERE id = :id AND state = 'PENDING'")
    suspend fun applied(id: String)

    @Query("SELECT * FROM earned_rewards WHERE kind = 'ROUTINE' AND state = 'RUNNING'")
    suspend fun running(): List<EarnedReward>

    @Query("UPDATE earned_rewards SET state = :state WHERE id = :id AND state = 'RUNNING'")
    suspend fun finishRun(id: String, state: String)

    @Query(
        "DELETE FROM earned_rewards WHERE kind = 'HABIT_LOG' AND state = 'APPLIED' AND sourceId = :sourceId AND period = :period AND ordinal < (SELECT MAX(ordinal) FROM earned_rewards WHERE kind = 'HABIT_LOG' AND state = 'APPLIED' AND sourceId = :sourceId AND period = :period)"
    )
    suspend fun compactAppliedLogs(sourceId: Long, period: String)

    @Query("SELECT * FROM earned_rewards ORDER BY id") suspend fun all(): List<EarnedReward>

    @Query("DELETE FROM earned_rewards") suspend fun deleteAll()
}
