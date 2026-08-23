package io.github.nelurea.muninn.capture.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import io.github.nelurea.muninn.capture.storage.MediaStorage
import io.github.nelurea.muninn.capture.storage.MediaStorageResult
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.SaveKind
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.data.repository.SessionRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSaveCaptureUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var storage: FakeStorage
    private lateinit var useCase: SaveCaptureUseCase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        storage = FakeStorage()
        useCase = SaveCaptureUseCase(
            storage,
            CapturedWorkRepository(database),
            SessionRepository(database.sessionDao())
        )
    }

    @After fun tearDown() = database.close()

    @Test fun newAppendAndCompleteResaveJournalRequestedMediaAndPreserveCanonicalSession() = runBlocking {
        val first = useCase.save(draft(0, highlighted = setOf(0))) as SaveCaptureResult.Success
        val original = database.capturedWorkDao().getWithMediaById(first.workId)!!
        val originalSession = original.work.sessionId

        val append = useCase.save(
            draft(1, highlighted = setOf(0, 1)),
            requestedMediaIndices = setOf(0, 1),
            highlightedMediaIndices = setOf(0, 1)
        ) as SaveCaptureResult.Success
        val resave = useCase.save(
            draft(), requestedMediaIndices = setOf(0, 1), highlightedMediaIndices = setOf(1)
        ) as SaveCaptureResult.Success

        assertEquals(1, append.mediaCount)
        assertEquals(0, resave.mediaCount)
        assertEquals(2, storage.storeCalls)
        val events = database.saveEventDao().getBySourceIdentity("pixiv", "work")
        assertEquals(listOf(SaveKind.NEW_CAPTURE, SaveKind.MEDIA_APPEND, SaveKind.RESAVE), events.map { it.saveKind })
        assertTrue(events.all { it.sessionId != null })
        assertEquals(originalSession, database.capturedWorkDao().getWithMediaById(first.workId)!!.work.sessionId)
        val appendMedia = database.saveEventDao().getMedia(events[1].id)
        assertEquals(listOf(false, true), appendMedia.map { it.wasNewlyStored })
        assertTrue(appendMedia.all { it.wasRequested == true && it.wasHighlighted == true })
    }

    @Test fun duplicateIdentityUsesOldestCanonicalAndRankedMediaWithoutRewritingDuplicates() = runBlocking {
        val dao = database.capturedWorkDao()
        val newerId = dao.insertCapture(
            draft(1).toWork("2026-08-23T00:00:00.000Z"),
            listOf(media(0, "content://newer/0")), emptyList()
        )
        val olderId = dao.insertCapture(
            draft(1).toWork("2026-08-22T00:00:00.000Z"),
            listOf(media(0, "content://older/0")), emptyList()
        )

        val result = useCase.save(
            draft(), requestedMediaIndices = setOf(0), highlightedMediaIndices = setOf(0)
        ) as SaveCaptureResult.Success

        assertEquals(olderId, result.workId)
        assertEquals("content://older/0", CapturedWorkRepository(database)
            .getIdentitySnapshot("pixiv", "work")!!.mediaByIndex.getValue(0).localUri)
        assertEquals(1, dao.getWithMediaById(newerId)!!.media.size)
        assertEquals(1, dao.getWithMediaById(olderId)!!.media.size)
        assertEquals(0, storage.storeCalls)
    }

    private fun draft(vararg indices: Int, highlighted: Set<Int> = emptySet()) = CaptureDraft(
        sourceType = "pixiv", sourceId = "work", canonicalUrl = "https://example/work",
        capturedAt = "2026-08-23T00:00:00.000Z", authorId = "a", authorName = "A",
        title = "title", caption = "caption", tags = emptyList(),
        media = indices.map { index -> CaptureMediaDraft(
            mediaIndex = index, sourceUrl = "https://example/$index.jpg", mimeType = "image/jpeg",
            fileName = "$index.jpg", sourceFile = File("unused-$index"),
            isHighlighted = index in highlighted
        ) }
    )

    private fun CaptureDraft.toWork(timestamp: String) = io.github.nelurea.muninn.data.db.CapturedWorkEntity(
        sourceType = sourceType, sourceId = sourceId, canonicalUrl = canonicalUrl,
        capturedAt = timestamp, publishedAt = publishedAt, discoveryMode = discoveryMode,
        discoveryQuery = discoveryQuery, authorId = authorId, authorName = authorName,
        authorHandle = authorHandle, title = title, caption = caption, sessionId = null
    )

    private fun media(index: Int, uri: String) = CapturedMediaEntity(
        workId = 0, mediaIndex = index, localUri = uri,
        sourceUrl = "https://example/$index.jpg", mimeType = "image/jpeg",
        fileName = "$index.jpg", isHighlighted = false
    )

    private class FakeStorage : MediaStorage {
        var storeCalls = 0
        override suspend fun store(media: List<CaptureMediaDraft>): MediaStorageResult {
            storeCalls++
            return MediaStorageResult.Success(media.map { "content://stored/${storeCalls}/${it.mediaIndex}" })
        }
        override suspend fun delete(localUris: List<String>) = Unit
    }
}
