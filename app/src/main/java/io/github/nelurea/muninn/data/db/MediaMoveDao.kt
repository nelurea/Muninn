package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.nelurea.muninn.media.move.MediaMoveSourceIdentity

@Dao
abstract class MediaMoveDao {
    @Query("SELECT id FROM captured_media ORDER BY id")
    abstract suspend fun getAllMediaIds(): List<Long>

    @Query("SELECT * FROM captured_media WHERE id = :mediaId")
    abstract suspend fun getMedia(mediaId: Long): CapturedMediaEntity?

    @Query(
        """
        SELECT captured_works.sourceType, captured_works.sourceId
        FROM captured_media
        INNER JOIN captured_works ON captured_works.id = captured_media.workId
        WHERE captured_media.id = :mediaId
        """
    )
    abstract suspend fun getSourceIdentity(mediaId: Long): MediaMoveSourceIdentity?

    @Query("SELECT * FROM media_move_journal WHERE mediaId = :mediaId LIMIT 1")
    abstract suspend fun getJournal(mediaId: Long): MediaMoveJournalEntity?

    @Query("SELECT * FROM media_move_journal WHERE state != 'COMPLETED' ORDER BY id")
    abstract suspend fun getIncomplete(): List<MediaMoveJournalEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertJournal(journal: MediaMoveJournalEntity): Long

    @Query(
        """
        UPDATE media_move_journal
        SET sourceUri = :sourceUri, destinationRootUri = :destinationRootUri,
            destinationUri = NULL, state = 'PENDING', byteCount = NULL,
            lastError = NULL, updatedAt = :updatedAt
        WHERE mediaId = :mediaId AND state = 'COMPLETED'
        """
    )
    protected abstract suspend fun restartCompletedJournal(
        mediaId: Long,
        sourceUri: String,
        destinationRootUri: String?,
        updatedAt: Long
    ): Int

    @Transaction
    open suspend fun begin(
        mediaId: Long,
        destinationRootUri: String?,
        updatedAt: Long
    ): MediaMoveJournalEntity? {
        getJournal(mediaId)?.let { existing ->
            if (existing.state != MediaMoveState.COMPLETED) return existing
            val media = getMedia(mediaId) ?: return null
            restartCompletedJournal(mediaId, media.localUri, destinationRootUri, updatedAt)
            return getJournal(mediaId)
        }
        val media = getMedia(mediaId) ?: return null
        insertJournal(
            MediaMoveJournalEntity(
                mediaId = mediaId,
                sourceUri = media.localUri,
                destinationRootUri = destinationRootUri,
                updatedAt = updatedAt
            )
        )
        return getJournal(mediaId)
    }

    @Query(
        """
        UPDATE media_move_journal
        SET destinationUri = :destinationUri, state = 'COPYING',
            lastError = NULL, updatedAt = :updatedAt
        WHERE mediaId = :mediaId AND state IN ('PENDING', 'COPYING')
        """
    )
    abstract suspend fun markCopying(mediaId: Long, destinationUri: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE media_move_journal
        SET state = 'COPIED', byteCount = :byteCount,
            lastError = NULL, updatedAt = :updatedAt
        WHERE mediaId = :mediaId AND state = 'COPYING' AND destinationUri = :destinationUri
        """
    )
    abstract suspend fun markCopied(mediaId: Long, destinationUri: String, byteCount: Long, updatedAt: Long): Int

    @Query(
        """
        UPDATE media_move_journal
        SET lastError = :message, updatedAt = :updatedAt
        WHERE mediaId = :mediaId AND state != 'COMPLETED'
        """
    )
    abstract suspend fun recordError(mediaId: Long, message: String, updatedAt: Long)

    @Query(
        "UPDATE captured_media SET localUri = :destinationUri, fileName = :fileName " +
            "WHERE id = :mediaId AND localUri = :sourceUri"
    )
    protected abstract suspend fun compareAndSetDestination(
        mediaId: Long,
        sourceUri: String,
        destinationUri: String,
        fileName: String
    ): Int

    @Query("SELECT localUri FROM captured_media WHERE id = :mediaId")
    protected abstract suspend fun getLocalUri(mediaId: Long): String?

    @Query(
        """
        UPDATE media_move_journal
        SET state = 'DB_SWITCHED', lastError = NULL, updatedAt = :updatedAt
        WHERE mediaId = :mediaId AND state = 'COPIED'
        """
    )
    protected abstract suspend fun markDbSwitched(mediaId: Long, updatedAt: Long): Int

    @Transaction
    open suspend fun switchDatabase(journal: MediaMoveJournalEntity, fileName: String, updatedAt: Long): Boolean {
        val destinationUri = journal.destinationUri ?: return false
        val switched = compareAndSetDestination(journal.mediaId, journal.sourceUri, destinationUri, fileName)
        if (switched == 0 && getLocalUri(journal.mediaId) != destinationUri) return false
        return markDbSwitched(journal.mediaId, updatedAt) == 1
    }

    @Query(
        """
        UPDATE media_move_journal
        SET state = 'COMPLETED', lastError = NULL, updatedAt = :updatedAt
        WHERE mediaId = :mediaId AND state = 'DB_SWITCHED'
        """
    )
    abstract suspend fun markCompleted(mediaId: Long, updatedAt: Long): Int
}
