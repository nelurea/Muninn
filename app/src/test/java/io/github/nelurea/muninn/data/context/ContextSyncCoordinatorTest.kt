package io.github.nelurea.muninn.data.context

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ContextSyncCoordinatorTest {
    private val tree = "content://test/disposable-tree"

    @Test fun firstWriteIsVerifiedAndUnchangedDataDoesNotCreateANewGeneration() = runBlocking {
        val documents = FakeDocuments()
        val points = FakeCheckpoints()
        var content = archive(listOf("a".repeat(32)))
        val sync = sync(documents, points) { content }
        val first = sync.synchronize() as ContextSyncResult.Committed
        assertEquals(2, documents.files.size)
        val repeat = sync.synchronize() as ContextSyncResult.Unchanged
        assertEquals(first.receipt.generation, repeat.receipt.generation)
        assertEquals(2, documents.files.size)
        content = archive(listOf("a".repeat(32), "b".repeat(32)))
        val second = sync.synchronize() as ContextSyncResult.Committed
        assertNotEquals(first.receipt.generation, second.receipt.generation)
        assertEquals(4, documents.files.size)
        assertEquals(second.receipt.generation, points.current?.generation)
    }

    @Test fun existingExternalSnapshotWithoutLocalCheckpointBlocksAfterAppDataLoss() = runBlocking {
        val documents = FakeDocuments()
        val original = archive(listOf("a".repeat(32)))
        VerifiedContextJournal(documents).save(original)
        val points = FakeCheckpoints()
        val sync = sync(documents, points) { archive(emptyList()) }
        fails("restore/reconcile") { sync.synchronize() }
        assertEquals(2, documents.files.size)
        assertEquals(null, points.current)
    }

    @Test fun previouslyCommittedExternalSnapshotMissingIsNotSilentlyReplaced() = runBlocking {
        val documents = FakeDocuments()
        val points = FakeCheckpoints()
        val sync = sync(documents, points) { archive(listOf("a".repeat(32))) }
        sync.synchronize()
        documents.files.clear() // Disposable in-memory provider only; never real user storage.
        fails("missing") { sync.synchronize() }
        assertEquals(0, documents.files.size)
    }

    @Test fun secondWriterAdvancedGenerationBlocksInsteadOfOverwriting() = runBlocking {
        val documents = FakeDocuments()
        val points = FakeCheckpoints()
        val sync = sync(documents, points) { archive(listOf("a".repeat(32))) }
        sync.synchronize()
        VerifiedContextJournal(documents, clockMs = { 5000L })
            .save(archive(listOf("a".repeat(32), "b".repeat(32))))
        fails("advanced") { sync.synchronize() }
        assertEquals(4, documents.files.size)
    }

    @Test fun disappearingDurableIdentityBlocksDeletionUntilTombstonesAreSupported() = runBlocking {
        val documents = FakeDocuments()
        val points = FakeCheckpoints()
        var current = archive(listOf("a".repeat(32), "b".repeat(32)))
        val sync = sync(documents, points) { current }
        sync.synchronize()
        current = archive(listOf("a".repeat(32)))
        fails("identities disappeared") { sync.synchronize() }
        assertEquals(2, documents.files.size)
    }

    @Test fun failedCheckpointPersistenceCannotBeReportedAsSaved() = runBlocking {
        val documents = FakeDocuments()
        val points = FakeCheckpoints().apply { rejectWrites = true }
        val sync = sync(documents, points) { archive(listOf("a".repeat(32))) }
        fails("acknowledgment failed") { sync.synchronize() }
        assertEquals(null, points.current)
        assertEquals(2, documents.files.size) // A committed generation is preserved, not deleted.
        fails("restore/reconcile") { sync.synchronize() }
    }

    @Test fun invalidArchiveIsRejectedBeforeAnyExternalDocumentIsCreated() = runBlocking {
        val documents = FakeDocuments()
        val points = FakeCheckpoints()
        val sync = sync(documents, points) { "not a Context archive".toByteArray() }
        try {
            sync.synchronize()
            fail("Expected Context validation failure")
        } catch (_: IOException) {
            assertTrue(documents.files.isEmpty())
        }
    }

    private fun sync(
        docs: FakeDocuments,
        points: FakeCheckpoints,
        bytes: () -> ByteArray
    ) = ContextSyncCoordinator(docs, points, tree, bytes, nowMs = { 1000L })

    private suspend fun fails(message: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected failure containing: $message")
        } catch (e: IOException) {
            assertTrue("Unexpected: ${e.message}", e.message?.contains(message) == true)
        }
    }

    private fun archive(ids: List<String>): ByteArray {
        val tables = JSONObject()
        for (name in RoomContextSnapshotExporter.TABLE_NAMES) {
            val columns = JSONArray()
            val rows = JSONArray()
            if (name == "durable_identities") {
                columns.put("entityType").put("rowId").put("stableId")
                ids.forEachIndexed { index, id ->
                    rows.put(JSONArray().put("s:session").put("i:${index + 1}").put("s:$id"))
                }
            } else {
                columns.put("id")
                if (name == "sessions") {
                    ids.indices.forEach { index -> rows.put(JSONArray().put("i:${index + 1}")) }
                }
            }
            tables.put(name, JSONObject().put("columns", columns).put("rows", rows))
        }
        return JSONObject().put("format", RoomContextSnapshotExporter.FORMAT)
            .put("roomVersion", 23).put("tables", tables)
            .toString().toByteArray(Charsets.UTF_8)
    }

    private class FakeCheckpoints : ContextSyncCheckpointStore {
        var current: ContextSyncCheckpoint? = null
        var rejectWrites = false
        override fun load(treeKey: String) = current
        override fun save(treeKey: String, value: ContextSyncCheckpoint): Boolean {
            if (rejectWrites) return false
            current = value
            return true
        }
    }

    private class FakeDocuments : ContextDocumentStore {
        val files = linkedMapOf<String, ByteArray>()
        override fun list(): List<ContextDocument> = files.keys.map { ContextDocument(it, it) }
        override fun create(displayName: String, mimeType: String): ContextDocument {
            if (files.containsKey(displayName)) throw IOException("Duplicate fake document")
            files[displayName] = ByteArray(0)
            return ContextDocument(displayName, displayName)
        }
        override fun write(document: ContextDocument, bytes: ByteArray) {
            if (!files.containsKey(document.uri)) throw IOException("Document missing")
            files[document.uri] = bytes.copyOf()
        }
        override fun read(document: ContextDocument): ByteArray =
            files[document.uri]?.copyOf() ?: throw IOException("Document missing")
    }
}
