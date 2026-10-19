package io.github.nelurea.muninn.data.context

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class VerifiedContextJournalTest {
    private val first = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val second = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    @Test fun savesAndReadsVerifiedGeneration() {
        val store = MemoryStore()
        val journal = journal(store, first, 100L)
        val saved = journal.save("purpose:study".toByteArray())
        assertEquals(first, saved.generation)
        assertArrayEquals("purpose:study".toByteArray(), requireNotNull(journal.readLatest()).bytes)
        assertEquals(2, store.items.size)
    }

    @Test fun interruptedMarkerWriteDoesNotHidePreviousGeneration() {
        val store = MemoryStore()
        journal(store, first, 100L).save("old".toByteArray())
        store.failMarkerWrite = true
        assertThrows(IOException::class.java) {
            journal(store, second, 200L).save("new".toByteArray())
        }
        assertArrayEquals("old".toByteArray(), requireNotNull(journal(store, second, 200L).readLatest()).bytes)
    }

    @Test fun corruptedLatestPayloadFallsBackToOlderCommittedData() {
        val store = MemoryStore()
        journal(store, first, 100L).save("old".toByteArray())
        journal(store, second, 200L).save("new".toByteArray())
        val newer = store.items.values.single { it.ref.displayName.startsWith("muninn-context-data-v1-$second") }
        newer.bytes = "tampered".toByteArray()
        val recovered = requireNotNull(journal(store, second, 200L).readLatest())
        assertArrayEquals("old".toByteArray(), recovered.bytes)
        assertEquals(first, recovered.receipt.generation)
    }

    @Test fun failureBeforeCommitCannotReturnSuccess() {
        val store = MemoryStore()
        store.failDataRead = true
        assertThrows(IOException::class.java) {
            journal(store, first, 100L).save("context".toByteArray())
        }
        assertNull(journal(store, first, 100L).readLatest())
    }

    @Test fun corruptOnlyCommitReportsErrorRatherThanMissing() {
        val store = MemoryStore()
        journal(store, first, 100L).save("context".toByteArray())
        store.items.values.single { it.ref.displayName.startsWith("muninn-context-commit-v1-") }
            .bytes = "invalid".toByteArray()
        assertThrows(IOException::class.java) { journal(store, first, 100L).readLatest() }
    }

    private fun journal(store: MemoryStore, id: String, time: Long) =
        VerifiedContextJournal(store, clockMs = { time }, generationId = { id })

    private class MemoryStore : ContextDocumentStore {
        class Item(val ref: ContextDocument, var bytes: ByteArray = byteArrayOf())
        val items = linkedMapOf<String, Item>()
        var failMarkerWrite = false
        var failDataRead = false

        override fun list(): List<ContextDocument> = items.values.map { it.ref }
        override fun create(displayName: String, mimeType: String): ContextDocument {
            if (displayName in items) throw IOException("Duplicate document")
            val ref = ContextDocument(displayName, displayName)
            items[displayName] = Item(ref)
            return ref
        }
        override fun write(document: ContextDocument, bytes: ByteArray) {
            if (failMarkerWrite && document.displayName.startsWith("muninn-context-commit-v1-")) {
                items.getValue(document.uri).bytes = "partial".toByteArray()
                throw IOException("Simulated interrupted marker write")
            }
            items.getValue(document.uri).bytes = bytes.clone()
        }
        override fun read(document: ContextDocument): ByteArray {
            if (failDataRead && document.displayName.startsWith("muninn-context-data-v1-")) {
                throw IOException("Storage disconnected")
            }
            return items.getValue(document.uri).bytes.clone()
        }
    }
}
