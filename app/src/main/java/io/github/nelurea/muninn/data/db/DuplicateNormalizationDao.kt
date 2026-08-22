package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DuplicateNormalizationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(journal: DuplicateNormalizationJournalEntity): Long

    @Query("SELECT * FROM duplicate_normalization_journal WHERE state != 'COMPLETED' ORDER BY id")
    suspend fun getIncomplete(): List<DuplicateNormalizationJournalEntity>

    @Query("SELECT * FROM duplicate_normalization_journal WHERE id = :id")
    suspend fun get(id: Long): DuplicateNormalizationJournalEntity?

    @Query(
        """
        UPDATE duplicate_normalization_journal
        SET verificationState = :verificationState,
            verificationDetails = :details, updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING'
        """
    )
    suspend fun recordVerification(
        id: Long,
        verificationState: String,
        details: String?,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE duplicate_normalization_journal
        SET canonicalWorkId = :canonicalWorkId, planVersion = :planVersion,
            planJson = :planJson, state = 'PLANNED', lastError = NULL,
            updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING' AND verificationState = 'VERIFIED'
        """
    )
    suspend fun recordPlan(
        id: Long,
        canonicalWorkId: Long,
        planVersion: Int,
        planJson: String,
        updatedAt: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCleanup(entry: DuplicateCleanupJournalEntity): Long

    @Query("SELECT * FROM duplicate_cleanup_journal WHERE normalizationId = :normalizationId ORDER BY id")
    suspend fun getCleanup(normalizationId: Long): List<DuplicateCleanupJournalEntity>

    @Query(
        """
        UPDATE duplicate_cleanup_journal
        SET state = 'COMPLETED', lastError = NULL, updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING'
        """
    )
    suspend fun markCleanupCompleted(id: Long, updatedAt: Long): Int
}
