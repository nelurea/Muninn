package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import io.github.nelurea.muninn.capture.storage.MediaStorage
import io.github.nelurea.muninn.capture.storage.MediaStorageResult
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.db.SaveEventEntity
import io.github.nelurea.muninn.data.db.SaveEventMediaEntity
import io.github.nelurea.muninn.data.db.SaveKind
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCaptureUseCaseTest {

    @Test
    fun sameIdentityAcrossUseCaseInstancesStoresAndPersistsOnlyOnce() = runBlocking {
        val storage = BlockingMediaStorage()
        val repository = MultiIdentityPersistence()
        val first = SaveCaptureUseCase(storage, repository, FakeSessionStore())
        val second = SaveCaptureUseCase(storage, repository, FakeSessionStore())
        val draft = syntheticDraft(0)

        val firstResult = async(start = CoroutineStart.UNDISPATCHED) { first.save(draft) }
        storage.firstStoreEntered.await()
        val secondResult = async(start = CoroutineStart.UNDISPATCHED) { second.save(draft) }

        assertEquals(1, storage.storeCalls)
        storage.allowFirstStore.complete(Unit)

        assertTrue(firstResult.await() is SaveCaptureResult.Success)
        assertEquals(SaveCaptureResult.Success(1L, 0), secondResult.await())
        assertEquals(1, storage.storeCalls)
        assertEquals(1, repository.workCount)
        assertEquals(1, repository.mediaCount)
        assertEquals(2, repository.saveEvents.size)
        assertEquals(
            listOf(SaveKind.NEW_CAPTURE, SaveKind.RESAVE),
            repository.saveEvents.map { it.saveKind }
        )
        assertEquals(false, repository.saveEventMedia.single { it.saveEventId == 2L }.wasNewlyStored)
    }

    @Test
    fun differentIdentityProgressesWhileFirstIdentityWaitsForStorage() = runBlocking {
        val storage = BlockingMediaStorage()
        val repository = MultiIdentityPersistence()
        val useCase = SaveCaptureUseCase(storage, repository, FakeSessionStore())
        val firstDraft = syntheticDraft(0).copy(sourceId = "blocked")
        val secondDraft = syntheticDraft(0).copy(sourceId = "independent")

        val firstResult = async(start = CoroutineStart.UNDISPATCHED) { useCase.save(firstDraft) }
        storage.firstStoreEntered.await()
        val secondResult = useCase.save(secondDraft)

        assertTrue(secondResult is SaveCaptureResult.Success)
        assertEquals(2, storage.storeCalls)
        storage.allowFirstStore.complete(Unit)
        assertTrue(firstResult.await() is SaveCaptureResult.Success)
    }

    @Test
    fun touchFailureKeepsSuccessfulSave() = runBlocking {
        val sessionStore = FakeSessionStore(failTouch = true)
        val result = SaveCaptureUseCase(
            FakeMediaStorage(),
            FakeCapturePersistence(),
            sessionStore
        ).save(syntheticDraft(0))

        assertEquals(SaveCaptureResult.Success(123L, 1), result)
        assertEquals(listOf(1L), sessionStore.touchAttempts)
    }

    @Test
    fun resaveDoesNotChangeCanonicalWorkSession() = runBlocking {
        val repository = FakeCapturePersistence(
            existing = CapturedWorkWithMedia(
                work = syntheticWork(42L).copy(sessionId = 77L),
                media = emptyList(),
                tags = emptyList()
            )
        )

        val result = SaveCaptureUseCase(
            FakeMediaStorage(),
            repository,
            FakeSessionStore()
        ).save(syntheticDraft(0))

        assertTrue(result is SaveCaptureResult.Success)
        assertEquals(77L, repository.currentWork?.sessionId)
    }

    @Test
    fun resaveAppendsOnlyMissingTags() =
        runBlocking {
            val existing =
                CapturedWorkWithMedia(
                    work =
                        syntheticWork(
                            id =
                                42L
                        ),
                    media =
                        listOf(
                            CapturedMediaEntity(
                                id =
                                    100L,
                                workId =
                                    42L,
                                mediaIndex =
                                    0,
                                localUri =
                                    "existing://0",
                                sourceUrl =
                                    "https://synthetic.invalid/0.jpg",
                                mimeType =
                                    "image/jpeg",
                                fileName =
                                    "0.jpg",
                                isHighlighted =
                                    false
                            )
                        ),
                    tags =
                        listOf(
                            CapturedTagEntity(
                                workId =
                                    42L,
                                position =
                                    0,
                                tag =
                                    "existing"
                            )
                        )
                )

            val repository =
                FakeCapturePersistence(
                    existing =
                        existing
                )

            val useCase =
                SaveCaptureUseCase(
                    mediaStorage =
                        FakeMediaStorage(),
                    repository =
                        repository,
                    sessionRepository =
                        FakeSessionStore()
                )

            val draft =
                syntheticDraft(
                    0
                ).copy(
                    tags =
                        listOf(
                            "existing",
                            "Sensitive",
                            "Sensitive"
                        )
                )

            val firstResult =
                useCase.save(
                    draft
                )

            val secondResult =
                useCase.save(
                    draft
                )

            assertTrue(
                firstResult is
                    SaveCaptureResult.Success
            )

            assertTrue(
                secondResult is
                    SaveCaptureResult.Success
            )

            assertEquals(
                listOf(
                    "existing",
                    "Sensitive"
                ),
                repository.currentTags
            )
        }

    @Test
    fun persistenceFailureDeletesFilesForNewCapture() =
        runBlocking {
            val storage =
                FakeMediaStorage()

            val useCase =
                SaveCaptureUseCase(
                    mediaStorage =
                        storage,
                    repository =
                        FakeCapturePersistence(
                            failSave =
                                true
                        ),
                    sessionRepository =
                        FakeSessionStore()
                )

            val result =
                useCase.save(
                    syntheticDraft(
                        0,
                        1
                    )
                )

            assertTrue(
                result is SaveCaptureResult.Failure
            )

            assertEquals(
                listOf(
                    "fake://0",
                    "fake://1"
                ),
                storage.deletedUris
            )
        }

    @Test
    fun sessionFailureDeletesFilesForNewCapture() =
        runBlocking {
            val storage =
                FakeMediaStorage()

            val useCase =
                SaveCaptureUseCase(
                    mediaStorage =
                        storage,
                    repository =
                        FakeCapturePersistence(),
                    sessionRepository =
                        FakeSessionStore(
                            failResolve =
                                true
                        )
                )

            val result =
                useCase.save(
                    syntheticDraft(
                        0
                    )
                )

            assertTrue(
                result is SaveCaptureResult.Failure
            )

            assertEquals(
                emptyList<String>(),
                storage.deletedUris
            )
        }

    @Test
    fun appendFailureDeletesOnlyNewlyStoredFiles() =
        runBlocking {
            val existing =
                CapturedWorkWithMedia(
                    work =
                        syntheticWork(
                            id =
                                42L
                        ),
                    media =
                        listOf(
                            CapturedMediaEntity(
                                id =
                                    100L,
                                workId =
                                    42L,
                                mediaIndex =
                                    0,
                                localUri =
                                    "existing://0",
                                sourceUrl =
                                    "https://synthetic.invalid/0.jpg",
                                mimeType =
                                    "image/jpeg",
                                fileName =
                                    "0.jpg",
                                isHighlighted =
                                    false
                            )
                        ),
                    tags =
                        emptyList()
                )

            val storage =
                FakeMediaStorage()

            val useCase =
                SaveCaptureUseCase(
                    mediaStorage =
                        storage,
                    repository =
                        FakeCapturePersistence(
                            existing =
                                existing,
                            failAppend =
                                true
                        ),
                    sessionRepository =
                        FakeSessionStore()
                )

            val result =
                useCase.save(
                    syntheticDraft(
                        0,
                        1
                    )
                )

            assertTrue(
                result is SaveCaptureResult.Failure
            )

            assertEquals(
                listOf(
                    "fake://1"
                ),
                storage.deletedUris
            )

            assertTrue(
                "existing://0" !in
                    storage.deletedUris
            )
        }

    @Test
    fun successfulSaveDoesNotDeleteStoredFiles() =
        runBlocking {
            val storage =
                FakeMediaStorage()

            val useCase =
                SaveCaptureUseCase(
                    mediaStorage =
                        storage,
                    repository =
                        FakeCapturePersistence(),
                    sessionRepository =
                        FakeSessionStore()
                )

            val result =
                useCase.save(
                    syntheticDraft(
                        0,
                        1
                    )
                )

            assertTrue(
                result is SaveCaptureResult.Success
            )

            assertTrue(
                storage.deletedUris.isEmpty()
            )
        }

    private fun syntheticDraft(
        vararg indices: Int
    ): CaptureDraft {
        return CaptureDraft(
            sourceType =
                "pixiv",
            sourceId =
                "synthetic-work",
            canonicalUrl =
                "https://synthetic.invalid/work",
            capturedAt =
                "2026-08-17T00:00:00.000Z",
            publishedAt =
                null,
            discoveryMode =
                null,
            discoveryQuery =
                null,
            authorId =
                "synthetic-author",
            authorName =
                "Synthetic Author",
            authorHandle =
                null,
            title =
                "Synthetic Work",
            caption =
                "Synthetic caption",
            tags =
                emptyList(),
            media =
                indices.map {
                        index ->

                    CaptureMediaDraft(
                        mediaIndex =
                            index,
                        sourceUrl =
                            "https://synthetic.invalid/$index.jpg",
                        mimeType =
                            "image/jpeg",
                        fileName =
                            "$index.jpg",
                        sourceFile =
                            File(
                                "synthetic-$index.jpg"
                            ),
                        isHighlighted =
                            false
                    )
                }
        )
    }

    private fun syntheticWork(
        id: Long
    ): CapturedWorkEntity {
        return CapturedWorkEntity(
            id =
                id,
            sourceType =
                "pixiv",
            sourceId =
                "synthetic-work",
            canonicalUrl =
                "https://synthetic.invalid/work",
            capturedAt =
                "2026-08-17T00:00:00.000Z",
            publishedAt =
                null,
            discoveryMode =
                null,
            discoveryQuery =
                null,
            authorId =
                "synthetic-author",
            authorName =
                "Synthetic Author",
            authorHandle =
                null,
            title =
                "Synthetic Work",
            caption =
                "Synthetic caption",
            sessionId =
                1L
        )
    }

    private class FakeMediaStorage :
        MediaStorage {

        val deletedUris =
            mutableListOf<String>()

        override suspend fun store(
            media: List<CaptureMediaDraft>
        ): MediaStorageResult {
            return MediaStorageResult.Success(
                localUris =
                    media.map {
                        "fake://${it.mediaIndex}"
                    }
            )
        }

        override suspend fun delete(
            localUris: List<String>
        ) {
            deletedUris +=
                localUris
        }
    }

    private class FakeCapturePersistence(
        private val existing:
            CapturedWorkWithMedia? =
            null,
        private val failSave:
            Boolean =
            false,
        private val failAppend:
            Boolean =
            false
    ) : CapturePersistence {

        private var current = existing

        val currentWork: CapturedWorkEntity?
            get() = current?.work

        val currentTags: List<String>
            get() =
                current
                    ?.tags
                    ?.sortedBy {
                        it.position
                    }
                    ?.map {
                        it.tag
                    }
                    .orEmpty()

        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()

        override suspend fun getIdentitySnapshot(
            sourceType: String,
            sourceId: String
        ): CaptureIdentitySnapshot? = current?.toIdentitySnapshot()

        override suspend fun saveCapture(
            work: CapturedWorkEntity,
            media: List<CapturedMediaEntity>,
            tags: List<CapturedTagEntity>
        ): Long {
            if (
                failSave
            ) {
                error(
                    "synthetic persistence failure"
                )
            }

            current = CapturedWorkWithMedia(
                work = work.copy(id = 123L),
                media = emptyList(),
                tags = tags.map { it.copy(workId = 123L) }
            )
            return 123L
        }

        override suspend fun getBySourceIdentity(
            sourceType: String,
            sourceId: String
        ): CapturedWorkWithMedia? {
            return current
        }

        override suspend fun appendMediaToWork(
            workId: Long,
            media: List<CapturedMediaEntity>
        ) {
            if (
                failAppend
            ) {
                error(
                    "synthetic append failure"
                )
            }
            current = current?.let { capture ->
                capture.copy(
                    media = capture.media + media.mapIndexed { index, item ->
                        item.copy(id = 1000L + index, workId = workId)
                    }
                )
            }
        }

        override suspend fun appendTagsToWork(
            workId: Long,
            tags: List<CapturedTagEntity>
        ) {
            current =
                current?.let {
                        capture ->

                    capture.copy(
                        tags =
                            capture.tags +
                                tags.map {
                                    it.copy(
                                        workId =
                                            workId
                                    )
                                }
                    )
                }
        }

        override suspend fun markMediaHighlighted(
            workId: Long,
            mediaIndices: List<Int>
        ) {
        }

        override suspend fun markMediaHighlightedById(mediaIds: List<Long>) {
            current = current?.let { capture ->
                capture.copy(
                    media = capture.media.map { item ->
                        if (item.id in mediaIds) item.copy(isHighlighted = true) else item
                    }
                )
            }
        }

        override suspend fun insertSaveEvent(
            event: SaveEventEntity,
            media: List<SaveEventMediaEntity>
        ): Long = 1L
    }

    private class FakeSessionStore(
        private val failResolve:
            Boolean =
            false,
        private val failTouch: Boolean = false
    ) : CaptureSessionStore {

        val touchAttempts = mutableListOf<Long>()

        override suspend fun getOrCreateSession():
                Long {
            if (
                failResolve
            ) {
                error(
                    "synthetic session failure"
                )
            }

            return 1L
        }

        override suspend fun touch(
            sessionId: Long
        ) {
            touchAttempts += sessionId
            if (failTouch) error("synthetic touch failure")
        }
    }

    private class BlockingMediaStorage : MediaStorage {
        val firstStoreEntered = CompletableDeferred<Unit>()
        val allowFirstStore = CompletableDeferred<Unit>()
        var storeCalls = 0
            private set

        override suspend fun store(media: List<CaptureMediaDraft>): MediaStorageResult {
            storeCalls += 1
            if (storeCalls == 1) {
                firstStoreEntered.complete(Unit)
                allowFirstStore.await()
            }
            return MediaStorageResult.Success(media.map { "blocking://${it.mediaIndex}/$storeCalls" })
        }

        override suspend fun delete(localUris: List<String>) = Unit
    }

    private class MultiIdentityPersistence : CapturePersistence {
        private val captures = linkedMapOf<Pair<String, String>, CapturedWorkWithMedia>()
        private var nextWorkId = 1L
        private var nextMediaId = 1L
        private var nextSaveEventId = 1L

        val workCount: Int get() = captures.size
        val mediaCount: Int get() = captures.values.sumOf { it.media.size }
        val saveEvents = mutableListOf<SaveEventEntity>()
        val saveEventMedia = mutableListOf<SaveEventMediaEntity>()

        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()

        override suspend fun getIdentitySnapshot(
            sourceType: String,
            sourceId: String
        ): CaptureIdentitySnapshot? = captures[sourceType to sourceId]?.toIdentitySnapshot()

        override suspend fun saveCapture(
            work: CapturedWorkEntity,
            media: List<CapturedMediaEntity>,
            tags: List<CapturedTagEntity>
        ): Long {
            val id = nextWorkId++
            captures[work.sourceType to work.sourceId] = CapturedWorkWithMedia(
                work.copy(id = id),
                media,
                tags.map { it.copy(workId = id) }
            )
            return id
        }

        override suspend fun getBySourceIdentity(
            sourceType: String,
            sourceId: String
        ): CapturedWorkWithMedia? = captures[sourceType to sourceId]

        override suspend fun appendMediaToWork(workId: Long, media: List<CapturedMediaEntity>) {
            val entry = captures.entries.single { it.value.work.id == workId }
            captures[entry.key] = entry.value.copy(
                media = entry.value.media + media.map { it.copy(id = nextMediaId++, workId = workId) }
            )
        }

        override suspend fun appendTagsToWork(
            workId: Long,
            tags: List<CapturedTagEntity>
        ) {
            val entry =
                captures.entries.single {
                    it.value.work.id ==
                        workId
                }

            captures[entry.key] =
                entry.value.copy(
                    tags =
                        entry.value.tags +
                            tags.map {
                                it.copy(
                                    workId =
                                        workId
                                )
                            }
                )
        }

        override suspend fun markMediaHighlighted(
            workId: Long,
            mediaIndices: List<Int>
        ) = Unit

        override suspend fun markMediaHighlightedById(mediaIds: List<Long>) {
            captures.replaceAll { _, capture ->
                capture.copy(
                    media = capture.media.map { item ->
                        if (item.id in mediaIds) item.copy(isHighlighted = true) else item
                    }
                )
            }
        }

        override suspend fun insertSaveEvent(
            event: SaveEventEntity,
            media: List<SaveEventMediaEntity>
        ): Long {
            val eventId = nextSaveEventId++
            saveEvents += event.copy(id = eventId)
            saveEventMedia += media.map { it.copy(saveEventId = eventId) }
            return eventId
        }
    }

}

private fun CapturedWorkWithMedia.toIdentitySnapshot() = CaptureIdentitySnapshot(
    canonical = this,
    mediaByIndex = media.sortedBy { it.id }.associateBy { it.mediaIndex }
)
