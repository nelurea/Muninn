package io.github.nelurea.muninn.media.move

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.MediaMoveJournalEntity
import io.github.nelurea.muninn.data.db.MediaMoveState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMoveServiceTest {
    @Test
    fun `copy verify switch and delete preserve media id`() = runBlocking {
        val persistence = FakePersistence()
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        val result = service.move(42L, "content://tree/root")

        assertTrue(result is MediaMoveResult.Completed)
        assertEquals(42L, persistence.media.id)
        assertEquals("content://tree/root/image.jpg", persistence.media.localUri)
        assertEquals(MediaMoveState.COMPLETED, persistence.journal?.state)
        assertEquals(listOf("file:///old/image.jpg" to 42L), files.deleted)
        assertEquals(1, files.copyCount)
    }

    @Test
    fun `delete failure after database switch resumes without copying again`() = runBlocking {
        val persistence = FakePersistence()
        val files = FakeFiles(deleteSucceeds = false)
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, null) is MediaMoveResult.Failure)
        assertEquals(MediaMoveState.DB_SWITCHED, persistence.journal?.state)
        assertEquals("file:///internal/42/image.jpg", persistence.media.localUri)
        assertEquals(1, files.copyCount)

        files.deleteSucceeds = true
        assertTrue(service.resume(42L) is MediaMoveResult.Completed)
        assertEquals(1, files.copyCount)
        assertEquals(MediaMoveState.COMPLETED, persistence.journal?.state)
    }

    @Test
    fun `completed move can move the same media from internal to SD and back to internal`() = runBlocking {
        val persistence = FakePersistence()
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Completed)
        val sdUri = persistence.media.localUri
        assertEquals("content://tree/sd/image.jpg", sdUri)

        assertTrue(service.move(42L, null) is MediaMoveResult.Completed)

        assertEquals(42L, persistence.media.id)
        assertEquals("file:///internal/42/image.jpg", persistence.media.localUri)
        assertEquals(MediaMoveState.COMPLETED, persistence.journal?.state)
        assertEquals(listOf("file:///old/image.jpg" to 42L, sdUri to 42L), files.deleted)
        assertEquals(2, files.copyCount)
    }

    @Test
    fun `mark copying exception cleans up only the newly created destination`() = runBlocking {
        val persistence = FakePersistence(failNextMarkCopying = true)
        val files = FakeFiles(uniqueDestinationOnEachCreate = true)
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Failure)
        assertEquals(MediaMoveState.PENDING, persistence.journal?.state)
        assertEquals(0, files.destinations.size)
        assertEquals(listOf("content://tree/sd/image.jpg?created=1"), files.cleanedUp)
        assertEquals(emptyList<Pair<String, Long>>(), files.deleted)
        assertEquals(0, files.copyCount)

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Completed)

        assertEquals(1, files.destinations.size)
        assertEquals(2, files.createDestinationCount)
        assertEquals(listOf("content://tree/sd/image.jpg?created=1"), files.cleanedUp)
        assertEquals(1, files.copyCount)
        assertEquals("content://tree/sd/image.jpg?created=2", persistence.media.localUri)
    }

    @Test
    fun `mark copying false cleans up created destination without deleting source`() = runBlocking {
        val persistence = FakePersistence(returnFalseNextMarkCopying = true)
        val files = FakeFiles(uniqueDestinationOnEachCreate = true)
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Failure)

        assertEquals(listOf("content://tree/sd/image.jpg?created=1"), files.cleanedUp)
        assertEquals(emptyList<Pair<String, Long>>(), files.deleted)
        assertEquals("file:///old/image.jpg", persistence.media.localUri)
        assertEquals(MediaMoveState.PENDING, persistence.journal?.state)
    }

    @Test
    fun `retries clean each unrecorded destination without accumulating orphans`() = runBlocking {
        val persistence = FakePersistence(returnFalseMarkCopyingCount = 2)
        val files = FakeFiles(uniqueDestinationOnEachCreate = true)
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Failure)
        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Failure)
        assertEquals(MediaMoveResult.Completed(42L), service.move(42L, "content://tree/sd"))

        assertEquals(
            listOf(
                "content://tree/sd/image.jpg?created=1",
                "content://tree/sd/image.jpg?created=2"
            ),
            files.cleanedUp
        )
        assertEquals("content://tree/sd/image.jpg?created=3", persistence.media.localUri)
    }

    @Test
    fun `copy failure after mark copying keeps destination for resume`() = runBlocking {
        val persistence = FakePersistence()
        val files = FakeFiles(failNextCopy = true, uniqueDestinationOnEachCreate = true)
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Failure)
        val destination = persistence.journal?.destinationUri
        assertEquals(MediaMoveState.COPYING, persistence.journal?.state)
        assertEquals(emptyList<String>(), files.cleanedUp)

        assertEquals(MediaMoveResult.Completed(42L), service.resume(42L))
        assertEquals(destination, persistence.media.localUri)
        assertEquals(1, files.createDestinationCount)
        assertEquals(emptyList<String>(), files.cleanedUp)
    }

    @Test
    fun `new X move uses source identity and media index in requested file name`() = runBlocking {
        val persistence = FakePersistence().apply {
            identity = MediaMoveSourceIdentity("x", "1890123456789012345")
            media = media.copy(mediaIndex = 2, fileName = "legacy-name.jpg")
        }
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertEquals(MediaMoveResult.Completed(42L), service.move(42L, "content://tree/sd"))

        assertEquals(listOf("x-1890123456789012345-p2.jpg"), files.requestedFileNames)
        assertEquals("x-1890123456789012345-p2.jpg", persistence.media.fileName)
    }

    @Test
    fun `database stores provider assigned destination file name`() = runBlocking {
        val persistence = FakePersistence().apply {
            identity = MediaMoveSourceIdentity("x", "123")
        }
        val files = FakeFiles(providerFileName = "x-123-p0 (1).jpg")
        val service = MediaMoveService(persistence, files) { 10L }

        assertEquals(MediaMoveResult.Completed(42L), service.move(42L, "content://tree/sd"))

        assertEquals("x-123-p0 (1).jpg", persistence.media.fileName)
    }

    @Test
    fun `moving again to the same SAF root is already at destination without side effects`() = runBlocking {
        val persistence = FakePersistence()
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, "content://tree/sd") is MediaMoveResult.Completed)
        val sourceUri = persistence.media.localUri
        val copyCount = files.copyCount
        val deleted = files.deleted.toList()
        val databaseSwitchCount = persistence.databaseSwitchCount
        val beginCount = persistence.beginCount

        val result = service.move(42L, "content://tree/sd")

        assertEquals(MediaMoveResult.AlreadyAtDestination(42L), result)
        assertEquals(sourceUri, persistence.media.localUri)
        assertEquals(copyCount, files.copyCount)
        assertEquals(deleted, files.deleted)
        assertEquals(databaseSwitchCount, persistence.databaseSwitchCount)
        assertEquals(beginCount, persistence.beginCount)
    }

    @Test
    fun `moving again to the same internal destination is already at destination without side effects`() = runBlocking {
        val persistence = FakePersistence()
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertTrue(service.move(42L, null) is MediaMoveResult.Completed)
        val sourceUri = persistence.media.localUri
        val copyCount = files.copyCount
        val deleted = files.deleted.toList()
        val databaseSwitchCount = persistence.databaseSwitchCount
        val beginCount = persistence.beginCount

        val result = service.move(42L, null)

        assertEquals(MediaMoveResult.AlreadyAtDestination(42L), result)
        assertEquals(sourceUri, persistence.media.localUri)
        assertEquals(copyCount, files.copyCount)
        assertEquals(deleted, files.deleted)
        assertEquals(databaseSwitchCount, persistence.databaseSwitchCount)
        assertEquals(beginCount, persistence.beginCount)
    }

    @Test
    fun `media in normal internal storage skips before destination or journal creation`() = runBlocking {
        val persistence = FakePersistence().apply {
            media = media.copy(localUri = "file:///app/files/captured_media/existing/image.jpg")
        }
        val files = FakeFiles().apply { alreadyAtDestination += persistence.media.localUri }
        val service = MediaMoveService(persistence, files) { 10L }

        assertEquals(MediaMoveResult.AlreadyAtDestination(42L), service.move(42L, null))
        assertEquals(0, files.createDestinationCount)
        assertEquals(0, files.copyCount)
        assertEquals(emptyList<Pair<String, Long>>(), files.deleted)
        assertEquals(0, persistence.beginCount)
        assertEquals(0, persistence.databaseSwitchCount)
    }

    @Test
    fun `resume uses destination recorded in unfinished journal`() = runBlocking {
        val persistence = FakePersistence().apply {
            journal = MediaMoveJournalEntity(
                mediaId = media.id,
                sourceUri = media.localUri,
                destinationRootUri = "content://tree/recorded",
                updatedAt = 1L
            )
        }
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertEquals(MediaMoveResult.Completed(42L), service.resume(42L))
        assertEquals("content://tree/recorded/image.jpg", persistence.media.localUri)
    }

    @Test
    fun `resume copying uses recorded legacy destination without creating another document`() = runBlocking {
        val legacyDestination = "content://tree/recorded/.muninn-move-42/image.jpg"
        val persistence = FakePersistence().apply {
            journal = MediaMoveJournalEntity(
                mediaId = media.id,
                sourceUri = media.localUri,
                destinationRootUri = "content://tree/recorded",
                destinationUri = legacyDestination,
                state = MediaMoveState.COPYING,
                updatedAt = 1L
            )
        }
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertEquals(MediaMoveResult.Completed(42L), service.resume(42L))
        assertEquals(legacyDestination, persistence.media.localUri)
        assertEquals(0, files.createDestinationCount)
        assertEquals(1, files.copyCount)
    }

    @Test
    fun `db switched legacy source resumes delete only without create copy or switch`() = runBlocking {
        val legacySource = "content://tree/old/.muninn-move-42/image.jpg"
        val destination = "content://tree/new/image.jpg"
        val persistence = FakePersistence().apply {
            media = media.copy(localUri = destination)
            journal = MediaMoveJournalEntity(
                mediaId = media.id,
                sourceUri = legacySource,
                destinationRootUri = "content://tree/new",
                destinationUri = destination,
                byteCount = 123L,
                state = MediaMoveState.DB_SWITCHED,
                updatedAt = 1L
            )
        }
        val files = FakeFiles()
        val service = MediaMoveService(persistence, files) { 10L }

        assertEquals(MediaMoveResult.Completed(42L), service.resume(42L))
        assertEquals(listOf(legacySource to 42L), files.deleted)
        assertEquals(0, files.createDestinationCount)
        assertEquals(0, files.copyCount)
        assertEquals(0, persistence.databaseSwitchCount)
    }

    private class FakeFiles(
        var deleteSucceeds: Boolean = true,
        private val uniqueDestinationOnEachCreate: Boolean = false,
        var failNextCopy: Boolean = false,
        private val providerFileName: String? = null
    ) : MediaMoveFileOperations {
        var copyCount = 0
        var createDestinationCount = 0
        val destinations = mutableSetOf<String>()
        val cleanedUp = mutableListOf<String>()
        val deleted = mutableListOf<Pair<String, Long>>()
        val alreadyAtDestination = mutableSetOf<String>()
        val requestedFileNames = mutableListOf<String>()
        private val fileNamesByUri = mutableMapOf<String, String>()
        override suspend fun isAtDestination(sourceUri: String, destinationRootUri: String?): Boolean {
            val expected = destinationRootUri?.let { "$it/image.jpg" }
                ?: "file:///internal/42/image.jpg"
            return sourceUri == expected || sourceUri in alreadyAtDestination
        }
        override suspend fun createDestination(
            mediaId: Long,
            fileName: String,
            mimeType: String,
            destinationRootUri: String?
        ): String {
            createDestinationCount++
            requestedFileNames += fileName
            val actualFileName = providerFileName ?: fileName
            val destination = destinationRootUri?.let {
                "$it/$actualFileName" + if (uniqueDestinationOnEachCreate) "?created=$createDestinationCount" else ""
            }
                ?: "file:///internal/$mediaId/$actualFileName"
            destinations += destination
            fileNamesByUri[destination] = actualFileName
            return destination
        }
        override suspend fun getFileName(uri: String) =
            fileNamesByUri[uri] ?: uri.substringBefore('?').substringAfterLast('/')
        override suspend fun copyAndVerify(sourceUri: String, destinationUri: String): Long {
            copyCount++
            if (failNextCopy) {
                failNextCopy = false
                error("Copy failed")
            }
            return 123L
        }
        override suspend fun cleanupDestination(destinationUri: String): Boolean {
            cleanedUp += destinationUri
            destinations -= destinationUri
            return true
        }
        override suspend fun delete(uri: String, mediaId: Long): Boolean {
            deleted += uri to mediaId
            return deleteSucceeds
        }
    }

    private class FakePersistence(
        var failNextMarkCopying: Boolean = false,
        var returnFalseNextMarkCopying: Boolean = false,
        var returnFalseMarkCopyingCount: Int = 0
    ) : MediaMovePersistence {
        var beginCount = 0
        var databaseSwitchCount = 0
        var media = CapturedMediaEntity(
            id = 42L,
            workId = 1L,
            mediaIndex = 0,
            localUri = "file:///old/image.jpg",
            sourceUrl = "https://example.test/image.jpg",
            mimeType = "image/jpeg",
            fileName = "image.jpg",
            isHighlighted = false
        )
        var identity = MediaMoveSourceIdentity("pixiv", "123")
        var journal: MediaMoveJournalEntity? = null

        override suspend fun getAllMediaIds() = listOf(media.id)

        override suspend fun begin(mediaId: Long, destinationRootUri: String?, now: Long): MediaMoveJournalEntity? {
            beginCount++
            if (media.id != mediaId) return null
            if (journal == null || journal?.state == MediaMoveState.COMPLETED) {
                journal = MediaMoveJournalEntity(
                    mediaId = mediaId,
                    sourceUri = media.localUri,
                    destinationRootUri = destinationRootUri,
                    updatedAt = now
                )
            }
            return journal
        }
        override suspend fun getMedia(mediaId: Long) = media.takeIf { it.id == mediaId }
        override suspend fun getSourceIdentity(mediaId: Long) = identity.takeIf { media.id == mediaId }
        override suspend fun getJournal(mediaId: Long) = journal?.takeIf { it.mediaId == mediaId }
        override suspend fun getIncomplete() = listOfNotNull(journal).filter { it.state != MediaMoveState.COMPLETED }
        override suspend fun markCopying(mediaId: Long, destinationUri: String, now: Long): Boolean {
            if (failNextMarkCopying) {
                failNextMarkCopying = false
                error("Process ended before journal update")
            }
            if (returnFalseNextMarkCopying) {
                returnFalseNextMarkCopying = false
                return false
            }
            if (returnFalseMarkCopyingCount > 0) {
                returnFalseMarkCopyingCount--
                return false
            }
            journal = journal?.copy(destinationUri = destinationUri, state = MediaMoveState.COPYING, updatedAt = now)
            return true
        }
        override suspend fun markCopied(mediaId: Long, destinationUri: String, byteCount: Long, now: Long): Boolean {
            journal = journal?.copy(state = MediaMoveState.COPIED, byteCount = byteCount, updatedAt = now)
            return true
        }
        override suspend fun switchDatabase(journal: MediaMoveJournalEntity, fileName: String, now: Long): Boolean {
            databaseSwitchCount++
            if (media.localUri != journal.sourceUri) return false
            media = media.copy(localUri = requireNotNull(journal.destinationUri), fileName = fileName)
            this.journal = journal.copy(state = MediaMoveState.DB_SWITCHED, updatedAt = now)
            return true
        }
        override suspend fun markCompleted(mediaId: Long, now: Long): Boolean {
            journal = journal?.copy(state = MediaMoveState.COMPLETED, lastError = null, updatedAt = now)
            return true
        }
        override suspend fun recordError(mediaId: Long, message: String, now: Long) {
            journal = journal?.copy(lastError = message, updatedAt = now)
        }
    }
}
