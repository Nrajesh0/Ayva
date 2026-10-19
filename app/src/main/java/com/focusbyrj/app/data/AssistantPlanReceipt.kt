package com.focusbyrj.app.data

import androidx.room.*

/** Task-only save journal. Written in the same transaction as the plan's tasks. */
@Entity(tableName = "assistant_plan_receipts")
data class AssistantPlanReceipt(
    @PrimaryKey val draftId: String,
    val revision: Int,
    val payloadHash: String,
    val tasksJson: String,
    val createdAt: Long,
    val effectsPending: Boolean = true
)

@Dao
interface AssistantPlanReceiptDao {
    @Query("SELECT * FROM assistant_plan_receipts WHERE draftId = :id")
    suspend fun get(id: String): AssistantPlanReceipt?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: AssistantPlanReceipt)
    @Query("UPDATE assistant_plan_receipts SET effectsPending = 0 WHERE draftId = :id")
    suspend fun finishEffects(id: String)
    @Query("SELECT * FROM assistant_plan_receipts WHERE effectsPending = 1 ORDER BY createdAt LIMIT 30")
    suspend fun pending(): List<AssistantPlanReceipt>
    @Query("DELETE FROM assistant_plan_receipts WHERE createdAt < :before AND effectsPending = 0")
    suspend fun prune(before: Long)
}
