package io.github.nelurea.muninn.media.move

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MediaMoveBatchState(
    val processed: Int = 0,
    val total: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val failedIds: List<Long> = emptyList()
)

interface MediaMoveBatchOperations {
    suspend fun allMediaIds(): List<Long>
    suspend fun incompleteMediaIds(): List<Long>
    suspend fun move(mediaId: Long, destinationRootUri: String?): MediaMoveResult
    suspend fun resume(mediaId: Long): MediaMoveResult
}

class MediaMoveBatchCoordinator(
    private val service: MediaMoveBatchOperations
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(MediaMoveBatchState())
    val state: StateFlow<MediaMoveBatchState> = mutableState.asStateFlow()

    private var destinationSnapshot: String? = null
    private var retryResumesJournal = false

    suspend fun start(destinationRootUri: String?) = mutex.withLock {
        val mediaIds = service.allMediaIds().toList()
        destinationSnapshot = destinationRootUri
        retryResumesJournal = false
        process(mediaIds) { mediaId -> service.move(mediaId, destinationRootUri) }
    }

    suspend fun retryFailed() = mutex.withLock {
        val mediaIds = mutableState.value.failedIds.toList()
        val destination = destinationSnapshot
        process(mediaIds) { mediaId ->
            if (retryResumesJournal) service.resume(mediaId) else service.move(mediaId, destination)
        }
    }

    suspend fun resumeIncomplete() = mutex.withLock {
        val mediaIds = service.incompleteMediaIds().toList()
        destinationSnapshot = null
        retryResumesJournal = true
        process(mediaIds) { mediaId -> service.resume(mediaId) }
    }

    private suspend fun process(
        mediaIds: List<Long>,
        operation: suspend (Long) -> MediaMoveResult
    ) {
        mutableState.value = MediaMoveBatchState(total = mediaIds.size)
        mediaIds.forEach { mediaId ->
            val previous = mutableState.value
            val result = runCatching { operation(mediaId) }
                .getOrElse { MediaMoveResult.Failure(it.message ?: "Media move failed") }
            mutableState.value = when (result) {
                is MediaMoveResult.Completed -> previous.copy(
                    processed = previous.processed + 1,
                    completed = previous.completed + 1
                )
                is MediaMoveResult.AlreadyAtDestination -> previous.copy(
                    processed = previous.processed + 1,
                    skipped = previous.skipped + 1
                )
                is MediaMoveResult.Failure -> previous.copy(
                    processed = previous.processed + 1,
                    failed = previous.failed + 1,
                    failedIds = previous.failedIds + mediaId
                )
            }
        }
    }
}
