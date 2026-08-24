package io.github.nelurea.muninn.media.move

import io.github.nelurea.muninn.data.db.MediaMoveJournalEntity
import io.github.nelurea.muninn.data.db.MediaMoveState

class MediaMoveService(
    private val persistence: MediaMovePersistence,
    private val files: MediaMoveFileOperations,
    private val now: () -> Long = System::currentTimeMillis
) : MediaMoveBatchOperations {
    override suspend fun move(mediaId: Long, destinationRootUri: String?): MediaMoveResult {
        val existingJournal = persistence.getJournal(mediaId)
        if (existingJournal == null || existingJournal.state == MediaMoveState.COMPLETED) {
            val media = persistence.getMedia(mediaId)
                ?: return MediaMoveResult.Failure("Captured media does not exist")
            if (files.isAtDestination(media.localUri, destinationRootUri)) {
                return MediaMoveResult.AlreadyAtDestination(mediaId)
            }
        }
        val journal = persistence.begin(mediaId, destinationRootUri, now())
            ?: return MediaMoveResult.Failure("Captured media does not exist")
        if (journal.destinationRootUri != destinationRootUri) {
            return MediaMoveResult.Failure("This media already has a move to another destination")
        }
        return resume(mediaId)
    }

    suspend fun resumeIncomplete(): List<MediaMoveResult> =
        persistence.getIncomplete().map { resume(it.mediaId) }

    override suspend fun allMediaIds(): List<Long> = persistence.getAllMediaIds()

    override suspend fun incompleteMediaIds(): List<Long> = persistence.getIncomplete().map { it.mediaId }

    override suspend fun resume(mediaId: Long): MediaMoveResult {
        var unrecordedDestination: String? = null
        return try {
            var journal = persistence.getJournal(mediaId)
                ?: return MediaMoveResult.Failure("Move journal does not exist")

            if (journal.state == MediaMoveState.COMPLETED) return MediaMoveResult.Completed(mediaId)
            if (journal.state == MediaMoveState.PENDING) {
                val media = persistence.getMedia(mediaId)
                    ?: return fail(mediaId, "Captured media does not exist")
                val identity = persistence.getSourceIdentity(mediaId)
                    ?: return fail(mediaId, "Captured work does not exist")
                val requestedFileName = destinationFileName(media, identity)
                val destination = files.createDestination(
                    mediaId,
                    requestedFileName,
                    media.mimeType,
                    journal.destinationRootUri
                )
                if (journal.sourceUri == destination) {
                    return MediaMoveResult.AlreadyAtDestination(mediaId)
                }
                unrecordedDestination = destination
                check(persistence.markCopying(mediaId, destination, now())) { "Move state changed while preparing copy" }
                unrecordedDestination = null
                journal = requireJournal(mediaId)
            }

            if (journal.state == MediaMoveState.COPYING) {
                val destination = requireNotNull(journal.destinationUri)
                val count = files.copyAndVerify(journal.sourceUri, destination)
                check(persistence.markCopied(mediaId, destination, count, now())) { "Move state changed while copying" }
                journal = requireJournal(mediaId)
            }

            if (journal.state == MediaMoveState.COPIED) {
                val destination = requireNotNull(journal.destinationUri)
                val fileName = files.getFileName(destination)
                check(persistence.switchDatabase(journal, fileName, now())) { "Source URI no longer matches captured media" }
                journal = requireJournal(mediaId)
            }

            if (journal.state == MediaMoveState.DB_SWITCHED) {
                if (!files.delete(journal.sourceUri, mediaId)) {
                    return fail(mediaId, "Could not delete old media; retry is required")
                }
                check(persistence.markCompleted(mediaId, now())) { "Move state changed while completing" }
            }
            MediaMoveResult.Completed(mediaId)
        } catch (exception: Exception) {
            var message = exception.message ?: "Media move failed"
            unrecordedDestination?.let { destination ->
                val cleanedUp = runCatching { files.cleanupDestination(destination) }.getOrDefault(false)
                if (!cleanedUp) message += "; Could not clean up unrecorded destination"
            }
            fail(mediaId, message)
        }
    }

    private suspend fun requireJournal(mediaId: Long): MediaMoveJournalEntity =
        requireNotNull(persistence.getJournal(mediaId))

    private suspend fun fail(mediaId: Long, message: String): MediaMoveResult.Failure {
        persistence.recordError(mediaId, message, now())
        return MediaMoveResult.Failure(message)
    }

    private fun destinationFileName(
        media: io.github.nelurea.muninn.data.db.CapturedMediaEntity,
        identity: MediaMoveSourceIdentity
    ): String {
        if (identity.sourceType != "x") return media.fileName
        val extension = media.fileName.substringAfterLast('.', missingDelimiterValue = "")
        check(extension.isNotBlank()) { "X media file extension is missing" }
        return "x-${identity.sourceId}-p${media.mediaIndex}.$extension"
    }

}

sealed interface MediaMoveResult {
    data class Completed(val mediaId: Long) : MediaMoveResult
    data class AlreadyAtDestination(val mediaId: Long) : MediaMoveResult
    data class Failure(val message: String) : MediaMoveResult
}
