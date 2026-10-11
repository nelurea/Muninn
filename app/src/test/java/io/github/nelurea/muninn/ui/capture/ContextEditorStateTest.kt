package io.github.nelurea.muninn.ui.capture

import io.github.nelurea.muninn.data.db.AestheticResponseVocabularyEntity
import io.github.nelurea.muninn.data.db.AttractionVocabularyEntity
import io.github.nelurea.muninn.data.db.MediaFocusEntity
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextEditorStateTest {
    private class FakeSource : ContextEditorDataSource {
        val writes = mutableListOf<String>()
        var purposeRead: suspend () -> List<PurposeVocabularyEntity> = { emptyList() }
        var focusRead: suspend (Long) -> List<MediaFocusEntity> = { emptyList() }
        var purposeWrite: suspend (Long, String) -> Unit = { _, _ -> }
        var focusWrite: suspend (Long, Long?, String?) -> Unit = { _, _, _ -> }

        override suspend fun purposeVocabulary() = purposeRead()
        override suspend fun purposes(workId: Long) = emptyList<PurposeVocabularyEntity>()
        override suspend fun addPurpose(workId: Long, label: String) {
            writes += "purpose:$workId:$label"
            purposeWrite(workId, label)
        }
        override suspend fun removePurpose(workId: Long, vocabularyId: Long) {
            writes += "removePurpose:$workId:$vocabularyId"
        }
        override suspend fun attractionVocabulary() = emptyList<AttractionVocabularyEntity>()
        override suspend fun workAttractions(workId: Long) = emptyList<AttractionVocabularyEntity>()
        override suspend fun pageAttractions(mediaId: Long) = emptyList<AttractionVocabularyEntity>()
        override suspend fun addWorkAttraction(workId: Long, dimension: String, label: String) {
            writes += "workAttraction:$workId:$dimension:$label"
        }
        override suspend fun removeWorkAttraction(workId: Long, vocabularyId: Long) {
            writes += "removeWorkAttraction:$workId:$vocabularyId"
        }
        override suspend fun addPageAttraction(mediaId: Long, dimension: String, label: String) {
            writes += "pageAttraction:$mediaId:$dimension:$label"
        }
        override suspend fun removePageAttraction(mediaId: Long, vocabularyId: Long) {
            writes += "removePageAttraction:$mediaId:$vocabularyId"
        }
        override suspend fun responseVocabulary() = emptyList<AestheticResponseVocabularyEntity>()
        override suspend fun responses(workId: Long) = emptyList<AestheticResponseVocabularyEntity>()
        override suspend fun addResponse(workId: Long, label: String) {
            writes += "response:$workId:$label"
        }
        override suspend fun removeResponse(workId: Long, vocabularyId: Long) {
            writes += "removeResponse:$workId:$vocabularyId"
        }
        override suspend fun focuses(mediaId: Long) = focusRead(mediaId)
        override suspend fun addFocus(mediaId: Long, attractionId: Long?, note: String?) {
            writes += "focus:$mediaId:$attractionId:$note"
            focusWrite(mediaId, attractionId, note)
        }
        override suspend fun removeFocus(focusId: Long) {
            writes += "removeFocus:$focusId"
        }
    }

    private fun focus(id: Long, mediaId: Long) = MediaFocusEntity(
        id = id, mediaId = mediaId, attractionVocabularyId = null, note = "note",
        regionLeft = null, regionTop = null, regionRight = null, regionBottom = null
    )

    @Test
    fun `late work read cannot replace a different work`() = runBlocking {
        val source = FakeSource()
        val firstRead = CompletableDeferred<List<PurposeVocabularyEntity>>()
        source.purposeRead = { firstRead.await() }
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        val old = async { state.loadPurpose(1) }
        yield()
        state.bindTarget(2, 20)
        firstRead.complete(listOf(PurposeVocabularyEntity(id = 8, label = "old")))
        old.await()
        assertTrue(state.purposeVocabulary(2).isEmpty())
        assertFalse(state.purposeLoaded(2))
        source.purposeRead = { listOf(PurposeVocabularyEntity(id = 9, label = "new")) }
        state.loadPurpose(2)
        assertEquals("new", state.purposeVocabulary(2).single().label)
    }

    @Test
    fun `later read wins on same work even if older read completes last`() = runBlocking {
        val source = FakeSource()
        val firstRead = CompletableDeferred<List<PurposeVocabularyEntity>>()
        var reads = 0
        source.purposeRead = {
            reads++
            if (reads == 1) firstRead.await()
            else listOf(PurposeVocabularyEntity(id = 2, label = "fresh"))
        }
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        val earlier = async { state.loadPurpose(1) }
        yield()
        state.loadPurpose(1)
        firstRead.complete(listOf(PurposeVocabularyEntity(id = 1, label = "stale")))
        earlier.await()
        assertEquals("fresh", state.purposeVocabulary(1).single().label)
    }

    @Test
    fun `page switch keeps valid work data but hides old focus`() = runBlocking {
        val source = FakeSource()
        source.purposeRead = { listOf(PurposeVocabularyEntity(id = 1, label = "reference")) }
        source.focusRead = { mediaId -> listOf(focus(mediaId, mediaId)) }
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        state.loadPurpose(1)
        state.loadFocus(1, 10)
        state.bindTarget(1, 11)
        assertEquals("reference", state.purposeVocabulary(1).single().label)
        assertTrue(state.focuses(1, 11).isEmpty())
        assertFalse(state.focusesLoaded(1, 11))
        state.loadFocus(1, 11)
        assertEquals(11L, state.focuses(1, 11).single().mediaId)
    }

    @Test
    fun `switching away and back does not revive previously loaded work`() = runBlocking {
        val source = FakeSource()
        source.purposeRead = { listOf(PurposeVocabularyEntity(id = 1, label = "previous")) }
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        state.loadPurpose(1)
        state.bindTarget(2, 20)
        state.bindTarget(1, 10)
        assertTrue(state.purposeVocabulary(1).isEmpty())
        assertFalse(state.purposeLoaded(1))
        source.purposeRead = { listOf(PurposeVocabularyEntity(id = 2, label = "current")) }
        state.loadPurpose(1)
        assertEquals("current", state.purposeVocabulary(1).single().label)
    }

    @Test
    fun `in flight page mutation writes to captured page and does not refresh new page`() = runBlocking {
        val source = FakeSource()
        val finish = CompletableDeferred<Unit>()
        source.focusWrite = { _, _, _ -> finish.await() }
        source.focusRead = { mediaId -> listOf(focus(mediaId, mediaId)) }
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        val pending = async { state.addFocus(1, 10, 5, "saved on page ten") }
        yield()
        state.bindTarget(1, 11)
        state.loadFocus(1, 11)
        finish.complete(Unit)
        assertFalse(pending.await())
        assertEquals(listOf("focus:10:5:saved on page ten"), source.writes)
        assertEquals(11L, state.focuses(1, 11).single().mediaId)
    }

    @Test
    fun `work change prevents writing with obsolete click target`() = runBlocking {
        val source = FakeSource()
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        state.bindTarget(2, 20)
        assertFalse(state.addPurpose(1, "old"))
        assertTrue(state.addPurpose(2, "new"))
        assertEquals(listOf("purpose:2:new"), source.writes)
    }

    @Test
    fun `focus delete rejects wrong page and accepts correct page`() = runBlocking {
        val source = FakeSource()
        val state = ContextEditorState(source)
        state.bindTarget(1, 10)
        assertFalse(state.removeFocus(1, 10, focus(4, 11)))
        assertTrue(state.removeFocus(1, 10, focus(5, 10)))
        assertEquals(listOf("removeFocus:5"), source.writes)
    }
}
