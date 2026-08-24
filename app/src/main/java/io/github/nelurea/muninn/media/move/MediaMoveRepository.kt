package io.github.nelurea.muninn.media.move

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.MediaMoveDao
import io.github.nelurea.muninn.data.db.MediaMoveJournalEntity

interface MediaMovePersistence {
    suspend fun getAllMediaIds(): List<Long>
    suspend fun begin(mediaId: Long, destinationRootUri: String?, now: Long): MediaMoveJournalEntity?
    suspend fun getMedia(mediaId: Long): CapturedMediaEntity?
    suspend fun getSourceIdentity(mediaId: Long): MediaMoveSourceIdentity?
    suspend fun getJournal(mediaId: Long): MediaMoveJournalEntity?
    suspend fun getIncomplete(): List<MediaMoveJournalEntity>
    suspend fun markCopying(mediaId: Long, destinationUri: String, now: Long): Boolean
    suspend fun markCopied(mediaId: Long, destinationUri: String, byteCount: Long, now: Long): Boolean
    suspend fun switchDatabase(journal: MediaMoveJournalEntity, fileName: String, now: Long): Boolean
    suspend fun markCompleted(mediaId: Long, now: Long): Boolean
    suspend fun recordError(mediaId: Long, message: String, now: Long)
}

class MediaMoveRepository(private val dao: MediaMoveDao) : MediaMovePersistence {
    override suspend fun getAllMediaIds() = dao.getAllMediaIds()
    override suspend fun begin(mediaId: Long, destinationRootUri: String?, now: Long) =
        dao.begin(mediaId, destinationRootUri, now)
    override suspend fun getMedia(mediaId: Long) = dao.getMedia(mediaId)
    override suspend fun getSourceIdentity(mediaId: Long) = dao.getSourceIdentity(mediaId)
    override suspend fun getJournal(mediaId: Long) = dao.getJournal(mediaId)
    override suspend fun getIncomplete() = dao.getIncomplete()
    override suspend fun markCopying(mediaId: Long, destinationUri: String, now: Long) =
        dao.markCopying(mediaId, destinationUri, now) == 1
    override suspend fun markCopied(mediaId: Long, destinationUri: String, byteCount: Long, now: Long) =
        dao.markCopied(mediaId, destinationUri, byteCount, now) == 1
    override suspend fun switchDatabase(journal: MediaMoveJournalEntity, fileName: String, now: Long) =
        dao.switchDatabase(journal, fileName, now)
    override suspend fun markCompleted(mediaId: Long, now: Long) = dao.markCompleted(mediaId, now) == 1
    override suspend fun recordError(mediaId: Long, message: String, now: Long) = dao.recordError(mediaId, message, now)
}

data class MediaMoveSourceIdentity(val sourceType: String, val sourceId: String)
