package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class DuplicateNormalizationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(journal: DuplicateNormalizationJournalEntity): Long

    @Query(
        """
        SELECT sourceType, sourceId, COUNT(*) AS workCount
        FROM captured_works
        GROUP BY sourceType, sourceId
        HAVING COUNT(*) > 1
        ORDER BY sourceType, sourceId
        """
    )
    abstract suspend fun getDuplicateIdentities(): List<DuplicateIdentity>

    @Query(
        "SELECT * FROM duplicate_normalization_journal WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1"
    )
    abstract suspend fun getByIdentity(sourceType: String, sourceId: String): DuplicateNormalizationJournalEntity?

    @Transaction
    open suspend fun ensureJournal(sourceType: String, sourceId: String, now: Long): DuplicateNormalizationJournalEntity {
        insert(DuplicateNormalizationJournalEntity(sourceType = sourceType, sourceId = sourceId, createdAt = now, updatedAt = now))
        return requireNotNull(getByIdentity(sourceType, sourceId))
    }

    @Query("SELECT * FROM duplicate_normalization_journal WHERE state != 'COMPLETED' ORDER BY id")
    abstract suspend fun getIncomplete(): List<DuplicateNormalizationJournalEntity>

    @Query("SELECT * FROM duplicate_normalization_journal WHERE id = :id")
    abstract suspend fun get(id: Long): DuplicateNormalizationJournalEntity?

    @Query("SELECT * FROM captured_works WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY capturedAt, id")
    protected abstract suspend fun getWorks(sourceType: String, sourceId: String): List<CapturedWorkEntity>

    @Query(
        """
        SELECT captured_media.* FROM captured_media
        INNER JOIN captured_works ON captured_works.id = captured_media.workId
        WHERE captured_works.sourceType = :sourceType AND captured_works.sourceId = :sourceId
        ORDER BY captured_media.mediaIndex, captured_media.workId, captured_media.id
        """
    )
    protected abstract suspend fun getMedia(sourceType: String, sourceId: String): List<CapturedMediaEntity>

    @Query(
        """
        SELECT media_move_journal.* FROM media_move_journal
        INNER JOIN captured_media ON captured_media.id = media_move_journal.mediaId
        INNER JOIN captured_works ON captured_works.id = captured_media.workId
        WHERE captured_works.sourceType = :sourceType AND captured_works.sourceId = :sourceId
          AND media_move_journal.state != 'COMPLETED'
        ORDER BY media_move_journal.mediaId
        """
    )
    protected abstract suspend fun getActiveMoves(sourceType: String, sourceId: String): List<MediaMoveJournalEntity>

    @Transaction
    open suspend fun snapshot(sourceType: String, sourceId: String) = DuplicateIdentitySnapshot(
        works = getWorks(sourceType, sourceId),
        media = getMedia(sourceType, sourceId),
        activeMoves = getActiveMoves(sourceType, sourceId)
    )

    @Query(
        """
        UPDATE duplicate_normalization_journal
        SET verificationState = :verificationState,
            verificationDetails = :details, updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING'
        """
    )
    protected abstract suspend fun recordVerification(
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
    protected abstract suspend fun recordPlan(
        id: Long,
        canonicalWorkId: Long,
        planVersion: Int,
        planJson: String,
        updatedAt: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCleanup(entry: DuplicateCleanupJournalEntity): Long

    @Query("SELECT * FROM duplicate_cleanup_journal WHERE normalizationId = :normalizationId ORDER BY id")
    abstract suspend fun getCleanup(normalizationId: Long): List<DuplicateCleanupJournalEntity>

    @Query(
        """
        UPDATE duplicate_cleanup_journal
        SET state = 'COMPLETED', lastError = NULL, updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING'
        """
    )
    abstract suspend fun markCleanupCompleted(id: Long, updatedAt: Long): Int

    @Transaction
    open suspend fun finalizeVerification(
        journalId: Long,
        expectedSnapshot: DuplicateIdentitySnapshot,
        verification: DuplicateVerification,
        planVersion: Int,
        planJson: String?,
        canonicalWorkId: Long?,
        now: Long
    ): Boolean {
        val journal = get(journalId) ?: return false
        if (journal.state != DuplicateNormalizationState.PENDING) return false
        val current = snapshot(journal.sourceType, journal.sourceId)
        val finalVerification = if (current == expectedSnapshot) verification else {
            DuplicateVerification.rejected(DuplicateVerificationDetails.SNAPSHOT_CHANGED)
        }
        if (finalVerification.state != DuplicateVerificationState.VERIFIED) {
            return recordVerification(journalId, finalVerification.state, finalVerification.details, now) == 1
        }
        if (canonicalWorkId == null || planJson == null) return false
        check(recordVerification(journalId, finalVerification.state, finalVerification.details, now) == 1)
        check(recordPlan(journalId, canonicalWorkId, planVersion, planJson, now) == 1)
        return true
    }
}

data class DuplicateIdentity(val sourceType: String, val sourceId: String, val workCount: Int)

data class DuplicateIdentitySnapshot(
    val works: List<CapturedWorkEntity>,
    val media: List<CapturedMediaEntity>,
    val activeMoves: List<MediaMoveJournalEntity>
)

data class DuplicateVerification(val state: String, val details: String?) {
    companion object {
        fun verified() = DuplicateVerification(DuplicateVerificationState.VERIFIED, null)
        fun rejected(reason: String) = DuplicateVerification(DuplicateVerificationState.REJECTED, reason)
    }
}

object DuplicateVerificationDetails {
    const val SIZE_MISMATCH = "SIZE_MISMATCH"
    const val HASH_MISMATCH = "HASH_MISMATCH"
    const val UNREADABLE = "UNREADABLE"
    const val SNAPSHOT_CHANGED = "SNAPSHOT_CHANGED"
    const val MEDIA_MOVE_IN_PROGRESS = "MEDIA_MOVE_IN_PROGRESS"
}
