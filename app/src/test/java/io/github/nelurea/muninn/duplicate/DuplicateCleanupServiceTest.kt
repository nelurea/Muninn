package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.DuplicateCleanupJournalEntity
import io.github.nelurea.muninn.data.db.DuplicateCleanupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateCleanupServiceTest {
    @Test fun `pending success completes entry`() = runBlocking {
        val persistence = FakePersistence(entry())
        run(persistence) { DuplicateCleanupDeleteResult.Deleted }
        assertEquals(DuplicateCleanupState.COMPLETED, persistence.entries.single().state)
        assertEquals(null, persistence.entries.single().lastError)
    }

    @Test fun `completed entry is skipped`() = runBlocking {
        val persistence = FakePersistence(entry(state = DuplicateCleanupState.COMPLETED))
        var deletes = 0
        run(persistence) { deletes++; DuplicateCleanupDeleteResult.Deleted }
        assertEquals(0, deletes)
    }

    @Test fun `shared uri remains pending without physical deletion`() = runBlocking {
        val persistence = FakePersistence(entry()).apply { references["file"] = 1 }
        var deletes = 0
        run(persistence) { deletes++; DuplicateCleanupDeleteResult.Deleted }
        assertEquals(0, deletes)
        assertPendingError(persistence, DuplicateCleanupError.URI_STILL_REFERENCED)
    }

    @Test fun `missing file is idempotent success`() = runBlocking {
        val persistence = FakePersistence(entry())
        run(persistence) { DuplicateCleanupDeleteResult.Missing }
        assertEquals(DuplicateCleanupState.COMPLETED, persistence.entries.single().state)
    }

    @Test fun `file not found result is idempotent success`() = runBlocking {
        val persistence = FakePersistence(entry())
        run(persistence) { DuplicateCleanupDeleteResult.Missing }
        assertEquals(DuplicateCleanupState.COMPLETED, persistence.entries.single().state)
    }

    @Test fun `delete failure records fixed code and remains pending`() = runBlocking {
        val persistence = FakePersistence(entry())
        run(persistence) { DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.DELETE_FAILED) }
        assertPendingError(persistence, DuplicateCleanupError.DELETE_FAILED)
    }

    @Test fun `retry after delete and completion CAS failure recovers from missing target`() = runBlocking {
        val persistence = FakePersistence(entry()).apply { failNextCompletionCas = true }
        var exists = true
        run(persistence) {
            if (exists) {
                exists = false
                DuplicateCleanupDeleteResult.Deleted
            } else DuplicateCleanupDeleteResult.Missing
        }
        assertEquals(DuplicateCleanupState.PENDING, persistence.entries.single().state)
        run(persistence) { if (exists) DuplicateCleanupDeleteResult.Deleted else DuplicateCleanupDeleteResult.Missing }
        assertEquals(DuplicateCleanupState.COMPLETED, persistence.entries.single().state)
    }

    @Test fun `one failed entry does not stop later entries`() = runBlocking {
        val persistence = FakePersistence(entry(1), entry(2, "second"))
        run(persistence) { uri ->
            if (uri == "file") DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.DELETE_FAILED)
            else DuplicateCleanupDeleteResult.Deleted
        }
        assertEquals(DuplicateCleanupState.PENDING, persistence.entries[0].state)
        assertEquals(DuplicateCleanupState.COMPLETED, persistence.entries[1].state)
    }

    private suspend fun run(
        persistence: FakePersistence,
        delete: (String) -> DuplicateCleanupDeleteResult
    ) = DuplicateCleanupService(
        persistence,
        DuplicateCleanupDeleter(delete),
        now = { 2L },
        ioDispatcher = Dispatchers.Unconfined
    ).run()

    private fun assertPendingError(persistence: FakePersistence, error: String) {
        assertEquals(DuplicateCleanupState.PENDING, persistence.entries.single().state)
        assertEquals(error, persistence.entries.single().lastError)
    }

    private fun entry(id: Long = 1, uri: String = "file", state: String = DuplicateCleanupState.PENDING) =
        DuplicateCleanupJournalEntity(id, 10, null, uri, state, null, 1)

    private class FakePersistence(vararg initial: DuplicateCleanupJournalEntity) : DuplicateCleanupPersistence {
        val entries = initial.toMutableList()
        val references = mutableMapOf<String, Int>()
        var failNextCompletionCas = false

        override suspend fun pending() = entries.filter { it.state == DuplicateCleanupState.PENDING }

        override suspend fun process(
            entry: DuplicateCleanupJournalEntity,
            now: Long,
            delete: (String) -> DuplicateCleanupDeleteResult
        ) {
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index < 0 || entries[index].state != DuplicateCleanupState.PENDING) return
            if (references[entry.targetUri] != null && references.getValue(entry.targetUri) > 0) {
                entries[index] = entries[index].copy(lastError = DuplicateCleanupError.URI_STILL_REFERENCED, updatedAt = now)
                return
            }
            when (val result = delete(entries[index].targetUri)) {
                DuplicateCleanupDeleteResult.Deleted, DuplicateCleanupDeleteResult.Missing -> {
                    if (failNextCompletionCas) failNextCompletionCas = false
                    else entries[index] = entries[index].copy(
                        state = DuplicateCleanupState.COMPLETED, lastError = null, updatedAt = now
                    )
                }
                is DuplicateCleanupDeleteResult.Failed ->
                    entries[index] = entries[index].copy(lastError = result.error, updatedAt = now)
            }
        }
    }
}
