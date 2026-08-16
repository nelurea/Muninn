package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.storage.MediaStorage
import io.github.nelurea.muninn.capture.storage.MediaStorageResult
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
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
    private val repository: CapturePersistence,
    private val sessionRepository: CaptureSessionStore
) {

    data class MediaSavePreparation(
        val workId: Long?,
        val missingMediaIndices: Set<Int>
    )

    suspend fun prepareMediaSave(
        sourceType: String,
        sourceId: String,
        requestedMediaIndices: Set<Int>,
        highlightedMediaIndices: Set<Int>
    ): MediaSavePreparation {

        val existing =
            repository.getBySourceIdentity(
                sourceType = sourceType,
                sourceId = sourceId
            )

        if (
            existing == null
        ) {
            return MediaSavePreparation(
                workId = null,
                missingMediaIndices =
                    requestedMediaIndices
            )
        }

        val existingIndices =
            existing.media
                .map {
                    it.mediaIndex
                }
                .toSet()

        repository.markMediaHighlighted(
            workId =
                existing.work.id,
            mediaIndices =
                highlightedMediaIndices
                    .filter {
                        it in existingIndices
                    }
        )

        return MediaSavePreparation(
            workId =
                existing.work.id,
            missingMediaIndices =
                requestedMediaIndices -
                    existingIndices
        )
    }
    suspend fun save(
        draft: CaptureDraft
    ): SaveCaptureResult {

        val existing =
            try {
                repository.getBySourceIdentity(
                    sourceType =
                        draft.sourceType,
                    sourceId =
                        draft.sourceId
                )
            } catch (
                exception: Exception
            ) {
                return SaveCaptureResult.Failure(
                    listOf(
                        "Could not check existing capture: ${exception.message}"
                    )
                )
            }

        if (
            existing != null
        ) {
            return appendToExistingCapture(
                draft = draft,
                workId = existing.work.id,
                existingMedia = existing.media
            )
        }

        return saveNewCapture(
            draft
        )
    }

    private suspend fun appendToExistingCapture(
        draft: CaptureDraft,
        workId: Long,
        existingMedia: List<CapturedMediaEntity>
    ): SaveCaptureResult {

        val existingIndices =
            existingMedia
                .map {
                    it.mediaIndex
                }
                .toSet()

        val missingMedia =
            draft.media
                .filter {
                    it.mediaIndex !in existingIndices
                }

        val highlightedExistingIndices =
            draft.media
                .filter {
                    it.mediaIndex in existingIndices &&
                        it.isHighlighted
                }
                .map {
                    it.mediaIndex
                }

        if (
            missingMedia.isEmpty()
        ) {
            return try {
                repository.markMediaHighlighted(
                    workId = workId,
                    mediaIndices = highlightedExistingIndices
                )

                SaveCaptureResult.Success(
                    workId = workId,
                    mediaCount = 0
                )
            } catch (
                exception: Exception
            ) {
                SaveCaptureResult.Failure(
                    listOf(
                        "Could not update existing capture: ${exception.message}"
                    )
                )
            }
        }

        val localUris =
            when (
                val result =
                    mediaStorage.store(
                        missingMedia
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

        val media =
            missingMedia.mapIndexed {
                    index,
                    item ->

                CapturedMediaEntity(
                    workId = workId,
                    mediaIndex = item.mediaIndex,
                    localUri = localUris[index],
                    sourceUrl = item.sourceUrl,
                    mimeType = item.mimeType,
                    fileName = item.fileName,
                    isHighlighted = item.isHighlighted
                )
            }

        return try {
            repository.appendMediaToWork(
                workId = workId,
                media = media
            )

            repository.markMediaHighlighted(
                workId = workId,
                mediaIndices = highlightedExistingIndices
            )

            SaveCaptureResult.Success(
                workId = workId,
                mediaCount = media.size
            )
        } catch (
            exception: Exception
        ) {
            runCatching {
                mediaStorage.delete(
                    localUris
                )
            }

            SaveCaptureResult.Failure(
                listOf(
                    "Could not update existing capture: ${exception.message}"
                )
            )
        }
    }

    private suspend fun saveNewCapture(
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
