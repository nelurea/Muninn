package io.github.nelurea.muninn.media.move

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMoveBatchCoordinatorTest {
    @Test
    fun `start snapshots ids and destination and continues after failure`() = runBlocking {
        val operations = FakeOperations(
            allIds = mutableListOf(1L, 2L, 3L),
            results = mutableMapOf(
                1L to MediaMoveResult.Completed(1L),
                2L to MediaMoveResult.Failure("broken"),
                3L to MediaMoveResult.AlreadyAtDestination(3L)
            )
        )
        val coordinator = MediaMoveBatchCoordinator(operations)

        coordinator.start("content://tree/one")

        assertEquals(listOf(1L, 2L, 3L), operations.movedIds)
        assertEquals(listOf("content://tree/one", "content://tree/one", "content://tree/one"), operations.destinations)
        assertEquals(MediaMoveBatchState(3, 3, 1, 1, 1, listOf(2L)), coordinator.state.value)
    }

    @Test
    fun `retry processes failed ids only with original destination`() = runBlocking {
        val operations = FakeOperations(
            allIds = mutableListOf(1L, 2L),
            results = mutableMapOf(
                1L to MediaMoveResult.Completed(1L),
                2L to MediaMoveResult.Failure("first failure")
            )
        )
        val coordinator = MediaMoveBatchCoordinator(operations)
        coordinator.start("content://tree/snapshot")
        operations.results[2L] = MediaMoveResult.Completed(2L)
        operations.allIds += 3L

        coordinator.retryFailed()

        assertEquals(listOf(1L, 2L, 2L), operations.movedIds)
        assertEquals("content://tree/snapshot", operations.destinations.last())
        assertEquals(MediaMoveBatchState(1, 1, 1, 0, 0), coordinator.state.value)
    }

    @Test
    fun `resume uses unfinished snapshot and recorded journal destinations`() = runBlocking {
        val operations = FakeOperations(
            incompleteIds = mutableListOf(8L, 9L),
            results = mutableMapOf(
                8L to MediaMoveResult.Completed(8L),
                9L to MediaMoveResult.Failure("still unavailable")
            )
        )
        val coordinator = MediaMoveBatchCoordinator(operations)

        coordinator.resumeIncomplete()

        assertEquals(listOf(8L, 9L), operations.resumedIds)
        assertEquals(emptyList<String?>(), operations.destinations)
        assertEquals(MediaMoveBatchState(2, 2, 1, 0, 1, listOf(9L)), coordinator.state.value)

        operations.results[9L] = MediaMoveResult.Completed(9L)
        coordinator.retryFailed()

        assertEquals(listOf(8L, 9L, 9L), operations.resumedIds)
        assertEquals(emptyList<Long>(), operations.movedIds)
        assertEquals(MediaMoveBatchState(1, 1, 1, 0, 0), coordinator.state.value)
    }

    private class FakeOperations(
        val allIds: MutableList<Long> = mutableListOf(),
        val incompleteIds: MutableList<Long> = mutableListOf(),
        val results: MutableMap<Long, MediaMoveResult> = mutableMapOf()
    ) : MediaMoveBatchOperations {
        val movedIds = mutableListOf<Long>()
        val resumedIds = mutableListOf<Long>()
        val destinations = mutableListOf<String?>()

        override suspend fun allMediaIds() = allIds
        override suspend fun incompleteMediaIds() = incompleteIds
        override suspend fun move(mediaId: Long, destinationRootUri: String?): MediaMoveResult {
            movedIds += mediaId
            destinations += destinationRootUri
            return requireNotNull(results[mediaId])
        }
        override suspend fun resume(mediaId: Long): MediaMoveResult {
            resumedIds += mediaId
            return requireNotNull(results[mediaId])
        }
    }
}
