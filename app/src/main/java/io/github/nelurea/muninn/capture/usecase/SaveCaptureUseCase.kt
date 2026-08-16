package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.storage.MediaStorage
import io.github.nelurea.muninn.capture.storage.MediaStorageResult
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.data.repository.SessionRepository

sealed interface SaveCaptureResult {

    data class Success(
        val workId: Long,
        val mediaCount: Int
    ) : SaveCaptureResult

    data class Failure(
        val errors: List<String>
    ) : SaveCaptureResult
}

class SaveCaptureUseCase(
    private val mediaStorage: MediaStorage,
    private val repository: CapturedWorkRepository,
    private val sessionRepository: SessionRepository
) {

    suspend fun save(
        draft: CaptureDraft
    ): SaveCaptureResult {

        val localUris =
            when (
                val result =
                    mediaStorage.store(
                        draft.media
                    )
            ) {
                is MediaStorageResult.Success ->
                    result.localUris

                is MediaStorageResult.Failure ->
                    return SaveCaptureResult.Failure(
                        listOf(
                            "Could not store media files: ${result.error}"
                        )
                    )
            }

        val sessionId =
            try {
                sessionRepository.getOrCreateSession()
            } catch (
                exception: Exception
            ) {
                runCatching {
                    mediaStorage.delete(
                        localUris
                    )
                }

                return SaveCaptureResult.Failure(
                    listOf(
                        "Could not resolve session: ${exception.message}"
                    )
                )
            }

        val work =
            CapturedWorkEntity(
                sourceType = draft.sourceType,
                sourceId = draft.sourceId,
                canonicalUrl = draft.canonicalUrl,
                capturedAt = draft.capturedAt,
                publishedAt = draft.publishedAt,
                discoveryMode = draft.discoveryMode,
                discoveryQuery = draft.discoveryQuery,
                authorId = draft.authorId,
                authorName = draft.authorName,
                authorHandle = draft.authorHandle,
                title = draft.title,
                caption = draft.caption,
                sessionId = sessionId
            )

        val media =
            draft.media.mapIndexed {
                    index,
                    item ->

                CapturedMediaEntity(
                    workId = 0,
                    mediaIndex = item.mediaIndex,
                    localUri = localUris[index],
                    sourceUrl = item.sourceUrl,
                    mimeType = item.mimeType,
                    fileName = item.fileName,
                    isHighlighted = item.isHighlighted
                )
            }

        val tags =
            draft.tags.mapIndexed {
                    index,
                    tag ->

                CapturedTagEntity(
                    workId = 0,
                    position = index,
                    tag = tag
                )
            }

        val workId =
            try {
                repository.saveCapture(
                    work = work,
                    media = media,
                    tags = tags
                )
            } catch (
                exception: Exception
            ) {
                runCatching {
                    mediaStorage.delete(
                        localUris
                    )
                }

                return SaveCaptureResult.Failure(
                    listOf(
                        "Could not persist capture: ${exception.message}"
                    )
                )
            }

        try {
            sessionRepository.touch(
                sessionId
            )
        } catch (
            _: Exception
        ) {
            // Capture persistence has already succeeded.
        }

        return SaveCaptureResult.Success(
            workId = workId,
            mediaCount = media.size
        )
    }
}