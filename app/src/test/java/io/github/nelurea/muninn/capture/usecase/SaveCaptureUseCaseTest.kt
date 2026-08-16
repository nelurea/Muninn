package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import io.github.nelurea.muninn.capture.storage.MediaStorage
import io.github.nelurea.muninn.capture.storage.MediaStorageResult
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCaptureUseCaseTest {

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
                listOf(
                    "fake://0"
                ),
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

            return 123L
        }

        override suspend fun getBySourceIdentity(
            sourceType: String,
            sourceId: String
        ): CapturedWorkWithMedia? {
            return existing
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
        }

        override suspend fun markMediaHighlighted(
            workId: Long,
            mediaIndices: List<Int>
        ) {
        }
    }

    private class FakeSessionStore(
        private val failResolve:
            Boolean =
            false
    ) : CaptureSessionStore {

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
        }
    }
}
