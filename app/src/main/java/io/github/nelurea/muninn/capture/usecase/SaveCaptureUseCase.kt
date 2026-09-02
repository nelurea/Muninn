package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import io.github.nelurea.muninn.capture.storage.MediaStorage
import io.github.nelurea.muninn.capture.storage.MediaStorageResult
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.SaveEventEntity
import io.github.nelurea.muninn.data.db.SaveEventMediaEntity
import io.github.nelurea.muninn.data.db.SaveEventOrigin
import io.github.nelurea.muninn.data.db.SaveKind
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SaveCaptureResult {
    data class Success(val workId: Long, val mediaCount: Int) : SaveCaptureResult
    data class Failure(val errors: List<String>) : SaveCaptureResult
}

class SaveCaptureUseCase(
    private val mediaStorage: MediaStorage,
    private val repository: CapturePersistence,
    private val sessionRepository: CaptureSessionStore
) {
    data class MediaSavePreparation(val workId: Long?, val missingMediaIndices: Set<Int>)

    /** Read-only hint. Correctness never relies on this result. */
    suspend fun prepareMediaSave(
        sourceType: String,
        sourceId: String,
        requestedMediaIndices: Set<Int>,
        highlightedMediaIndices: Set<Int>
    ): MediaSavePreparation {
        val existing = repository.getIdentitySnapshot(sourceType, sourceId)
        return MediaSavePreparation(
            existing?.canonical?.work?.id,
            requestedMediaIndices - existing.orEmptyIndices()
        )
    }

    suspend fun save(
        draft: CaptureDraft,
        requestedMediaIndices: Set<Int> = draft.media.mapTo(linkedSetOf()) { it.mediaIndex },
        highlightedMediaIndices: Set<Int> = draft.media.filter { it.isHighlighted }
            .mapTo(linkedSetOf()) { it.mediaIndex }
    ): SaveCaptureResult {
        val identity = Identity(draft.sourceType, draft.sourceId)
        return identityLocks.getOrPut(identity) { Mutex() }.withLock {
            saveLocked(draft, requestedMediaIndices, highlightedMediaIndices)
        }
    }

    private suspend fun saveLocked(
        draft: CaptureDraft,
        requestedMediaIndices: Set<Int>,
        highlightedMediaIndices: Set<Int>
    ): SaveCaptureResult {
        val createdUris = mutableListOf<String>()
        val redundantUris = mutableListOf<String>()
        return try {
            val sessionId = sessionRepository.getOrCreateSession()
            val before = repository.getIdentitySnapshot(draft.sourceType, draft.sourceId)
            val missing = requestedMediaIndices - before.orEmptyIndices()
            val draftsByIndex = draft.media.associateBy { it.mediaIndex }
            val mediaToStore = missing.mapNotNull(draftsByIndex::get)
            if (mediaToStore.map { it.mediaIndex }.toSet() != missing) {
                error("Downloaded media does not cover the final missing media set")
            }
            val storedByIndex = store(mediaToStore).also { createdUris += it.values }

            val outcome = repository.inTransaction {
                val preWrite = repository.getIdentitySnapshot(draft.sourceType, draft.sourceId)
                val stillMissing = requestedMediaIndices - preWrite.orEmptyIndices()
                redundantUris += storedByIndex.filterKeys { it !in stillMissing }.values

                val wasNew = preWrite == null
                val workId = if (wasNew) {
                    repository.saveCapture(
                        draft.toWork(sessionId),
                        emptyList(),
                        draft.tags.mapIndexed { index, tag ->
                            CapturedTagEntity(workId = 0, position = index, tag = tag)
                        }
                    )
                } else preWrite.canonical.work.id

                if (!wasNew) {
                    val existing =
                        requireNotNull(
                            preWrite
                        )

                    val existingTagNames =
                        existing
                            .canonical
                            .tags
                            .mapTo(
                                hashSetOf()
                            ) {
                                it.tag
                            }

                    val missingTags =
                        draft
                            .tags
                            .distinct()
                            .filterNot {
                                it in existingTagNames
                            }

                    val nextTagPosition =
                        (
                            existing
                                .canonical
                                .tags
                                .maxOfOrNull {
                                    it.position
                                }
                                ?: -1
                        ) + 1

                    repository.appendTagsToWork(
                        workId =
                            workId,
                        tags =
                            missingTags
                                .mapIndexed {
                                        index,
                                        tag ->

                                    CapturedTagEntity(
                                        workId =
                                            workId,
                                        position =
                                            nextTagPosition + index,
                                        tag =
                                            tag
                                    )
                                }
                    )
                }

                val inserted = stillMissing.map { index ->
                    val item = draftsByIndex.getValue(index)
                    CapturedMediaEntity(
                        workId = workId, mediaIndex = index,
                        localUri = storedByIndex.getValue(index), sourceUrl = item.sourceUrl,
                        mimeType = item.mimeType, fileName = item.fileName,
                        isHighlighted = index in highlightedMediaIndices
                    )
                }
                repository.appendMediaToWork(workId, inserted)
                val resolved = repository.getIdentitySnapshot(draft.sourceType, draft.sourceId)
                    ?: error("Capture disappeared during save")
                repository.markMediaHighlightedById(
                    highlightedMediaIndices.mapNotNull { resolved.mediaByIndex[it]?.id }
                )
                val newlyStored = inserted.mapTo(hashSetOf()) { it.mediaIndex }
                val saveKind = when {
                    wasNew -> SaveKind.NEW_CAPTURE
                    newlyStored.isNotEmpty() -> SaveKind.MEDIA_APPEND
                    else -> SaveKind.RESAVE
                }
                repository.insertSaveEvent(
                    SaveEventEntity(
                        sourceType = draft.sourceType, sourceId = draft.sourceId,
                        canonicalUrl = draft.canonicalUrl, savedAt = draft.capturedAt,
                        origin = SaveEventOrigin.CAPTURE, sessionId = sessionId,
                        canonicalWorkId = workId, discoveryMode = draft.discoveryMode,
                        discoveryQuery = draft.discoveryQuery, saveKind = saveKind
                    ),
                    requestedMediaIndices.sorted().map { index ->
                        val media = resolved.mediaByIndex[index]
                            ?: error("Requested media $index was not resolved after save")
                        SaveEventMediaEntity(
                            saveEventId = 0, capturedMediaId = media.id, mediaIndex = index,
                            localUri = media.localUri, sourceUrl = media.sourceUrl,
                            mimeType = media.mimeType, fileName = media.fileName,
                            wasRequested = true,
                            wasHighlighted = index in highlightedMediaIndices,
                            wasNewlyStored = index in newlyStored
                        )
                    }
                )
                SaveCaptureResult.Success(workId, inserted.size)
            }
            if (redundantUris.isNotEmpty()) runCatching { mediaStorage.delete(redundantUris) }
            runCatching { sessionRepository.touch(sessionId) }
            outcome
        } catch (exception: Exception) {
            if (createdUris.isNotEmpty()) runCatching { mediaStorage.delete(createdUris) }
            SaveCaptureResult.Failure(listOf("Could not save capture: ${exception.message}"))
        }
    }

    private suspend fun store(media: List<CaptureMediaDraft>): Map<Int, String> {
        if (media.isEmpty()) return emptyMap()
        return when (val result = mediaStorage.store(media)) {
            is MediaStorageResult.Success -> {
                require(result.localUris.size == media.size) { "Stored URI count does not match media count" }
                media.map { it.mediaIndex }.zip(result.localUris).toMap()
            }
            is MediaStorageResult.Failure -> error("Could not store media files: ${result.error}")
        }
    }

    private fun CaptureDraft.toWork(sessionId: Long) = CapturedWorkEntity(
        sourceType = sourceType, sourceId = sourceId, canonicalUrl = canonicalUrl,
        capturedAt = capturedAt, publishedAt = publishedAt, discoveryMode = discoveryMode,
        discoveryQuery = discoveryQuery, authorId = authorId, authorName = authorName,
        authorHandle = authorHandle, title = title, caption = caption, sessionId = sessionId
    )

    private fun CaptureIdentitySnapshot?.orEmptyIndices(): Set<Int> =
        this?.mediaByIndex?.keys ?: emptySet()

    private data class Identity(val sourceType: String, val sourceId: String)

    private companion object {
        val identityLocks = ConcurrentHashMap<Identity, Mutex>()
    }
}
