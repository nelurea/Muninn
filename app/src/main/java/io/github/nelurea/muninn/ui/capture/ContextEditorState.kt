package io.github.nelurea.muninn.ui.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.nelurea.muninn.data.db.AestheticResponseVocabularyEntity
import io.github.nelurea.muninn.data.db.AttractionVocabularyEntity
import io.github.nelurea.muninn.data.db.MediaFocusEntity
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository

/** The narrow persistence boundary for the existing Context Editor. */
internal interface ContextEditorDataSource {
    suspend fun purposeVocabulary(): List<PurposeVocabularyEntity>
    suspend fun purposes(workId: Long): List<PurposeVocabularyEntity>
    suspend fun addPurpose(workId: Long, label: String)
    suspend fun removePurpose(workId: Long, vocabularyId: Long)

    suspend fun attractionVocabulary(): List<AttractionVocabularyEntity>
    suspend fun workAttractions(workId: Long): List<AttractionVocabularyEntity>
    suspend fun pageAttractions(mediaId: Long): List<AttractionVocabularyEntity>
    suspend fun addWorkAttraction(workId: Long, dimension: String, label: String)
    suspend fun removeWorkAttraction(workId: Long, vocabularyId: Long)
    suspend fun addPageAttraction(mediaId: Long, dimension: String, label: String)
    suspend fun removePageAttraction(mediaId: Long, vocabularyId: Long)

    suspend fun responseVocabulary(): List<AestheticResponseVocabularyEntity>
    suspend fun responses(workId: Long): List<AestheticResponseVocabularyEntity>
    suspend fun addResponse(workId: Long, label: String)
    suspend fun removeResponse(workId: Long, vocabularyId: Long)

    suspend fun focuses(mediaId: Long): List<MediaFocusEntity>
    suspend fun addFocus(mediaId: Long, attractionId: Long?, note: String?)
    suspend fun removeFocus(focusId: Long)
}

/** Delegation only: retains all existing DAO, normalization and persistence semantics. */
internal class RepositoryContextEditorDataSource(
    private val repository: CapturedWorkRepository
) : ContextEditorDataSource {
    override suspend fun purposeVocabulary() = repository.getPurposeVocabulary()
    override suspend fun purposes(workId: Long) = repository.getPurposesForWork(workId)
    override suspend fun addPurpose(workId: Long, label: String) =
        repository.addPurposeToWork(workId, label)
    override suspend fun removePurpose(workId: Long, vocabularyId: Long) =
        repository.removePurposeFromWork(workId, vocabularyId)

    override suspend fun attractionVocabulary() = repository.getAttractionVocabulary()
    override suspend fun workAttractions(workId: Long) = repository.getAttractionsForWork(workId)
    override suspend fun pageAttractions(mediaId: Long) = repository.getAttractionsForMedia(mediaId)
    override suspend fun addWorkAttraction(workId: Long, dimension: String, label: String) =
        repository.addAttractionToWork(workId, dimension, label)
    override suspend fun removeWorkAttraction(workId: Long, vocabularyId: Long) =
        repository.removeAttractionFromWork(workId, vocabularyId)
    override suspend fun addPageAttraction(mediaId: Long, dimension: String, label: String) =
        repository.addAttractionToMedia(mediaId, dimension, label)
    override suspend fun removePageAttraction(mediaId: Long, vocabularyId: Long) =
        repository.removeAttractionFromMedia(mediaId, vocabularyId)

    override suspend fun responseVocabulary() = repository.getResponseVocabulary()
    override suspend fun responses(workId: Long) = repository.getResponsesForWork(workId)
    override suspend fun addResponse(workId: Long, label: String) =
        repository.addResponseToWork(workId, label)
    override suspend fun removeResponse(workId: Long, vocabularyId: Long) =
        repository.removeResponseFromWork(workId, vocabularyId)

    override suspend fun focuses(mediaId: Long) = repository.getFocusForMedia(mediaId)
    override suspend fun addFocus(mediaId: Long, attractionId: Long?, note: String?) {
        repository.addFocusToMedia(mediaId, attractionId, note)
    }
    override suspend fun removeFocus(focusId: Long) = repository.removeFocus(focusId)
}

private data class WorkValue<T>(val target: ContextTargetToken, val value: T) {
    val workId: Long get() = target.workId
}
private data class PageValue<T>(val target: ContextTargetToken, val value: T) {
    val workId: Long get() = target.workId
    val mediaId: Long? get() = target.mediaId
}

/**
 * State belongs to one sheet instance. The gate rejects both stale target results and
 * out-of-order refreshes. A page change does not invalidate work-scope data.
 */
internal class ContextEditorState(private val source: ContextEditorDataSource) {
    private val gate = ContextLoadGate()

    private var purposeVocabularyValue by mutableStateOf<WorkValue<List<PurposeVocabularyEntity>>?>(null)
    private var selectedPurposesValue by mutableStateOf<WorkValue<List<PurposeVocabularyEntity>>?>(null)
    private var attractionVocabularyValue by mutableStateOf<WorkValue<List<AttractionVocabularyEntity>>?>(null)
    private var selectedWorkAttractionsValue by mutableStateOf<WorkValue<List<AttractionVocabularyEntity>>?>(null)
    private var selectedPageAttractionsValue by mutableStateOf<PageValue<List<AttractionVocabularyEntity>>?>(null)
    private var responseVocabularyValue by mutableStateOf<WorkValue<List<AestheticResponseVocabularyEntity>>?>(null)
    private var selectedResponsesValue by mutableStateOf<WorkValue<List<AestheticResponseVocabularyEntity>>?>(null)
    private var focusesValue by mutableStateOf<PageValue<List<MediaFocusEntity>>?>(null)

    fun bindTarget(workId: Long, mediaId: Long?) = gate.bindTarget(workId, mediaId)

    fun purposeVocabulary(workId: Long) = purposeVocabularyValue.takeIf { it?.workId == workId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun selectedPurposes(workId: Long) = selectedPurposesValue.takeIf { it?.workId == workId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun attractionVocabulary(workId: Long) = attractionVocabularyValue.takeIf { it?.workId == workId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun workAttractions(workId: Long) = selectedWorkAttractionsValue.takeIf { it?.workId == workId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun pageAttractions(workId: Long, mediaId: Long?) = selectedPageAttractionsValue
        .takeIf { it?.workId == workId && it.mediaId == mediaId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun responseVocabulary(workId: Long) = responseVocabularyValue.takeIf { it?.workId == workId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun selectedResponses(workId: Long) = selectedResponsesValue.takeIf { it?.workId == workId && gate.isTargetCurrent(it.target) }?.value.orEmpty()
    fun focuses(workId: Long, mediaId: Long?) = focusesValue
        .takeIf { it?.workId == workId && it.mediaId == mediaId && gate.isTargetCurrent(it.target) }?.value.orEmpty()

    fun purposeLoaded(workId: Long) = purposeVocabularyValue?.takeIf { it.workId == workId && gate.isTargetCurrent(it.target) } != null &&
        selectedPurposesValue?.takeIf { it.workId == workId && gate.isTargetCurrent(it.target) } != null
    fun attractionVocabularyLoaded(workId: Long) = attractionVocabularyValue?.takeIf { it.workId == workId && gate.isTargetCurrent(it.target) } != null
    fun workAttractionsLoaded(workId: Long) = selectedWorkAttractionsValue?.takeIf { it.workId == workId && gate.isTargetCurrent(it.target) } != null
    fun pageAttractionsLoaded(workId: Long, mediaId: Long?) =
        selectedPageAttractionsValue?.takeIf { it.workId == workId && it.mediaId == mediaId && gate.isTargetCurrent(it.target) } != null
    fun responseLoaded(workId: Long) = responseVocabularyValue?.takeIf { it.workId == workId && gate.isTargetCurrent(it.target) } != null &&
        selectedResponsesValue?.takeIf { it.workId == workId && gate.isTargetCurrent(it.target) } != null
    fun focusesLoaded(workId: Long, mediaId: Long?) =
        focusesValue?.takeIf { it.workId == workId && it.mediaId == mediaId && gate.isTargetCurrent(it.target) } != null

    suspend fun loadPurpose(workId: Long) {
        val token = gate.beginLoad(ContextLoadSection.PURPOSE, workId) ?: return
        val vocabulary = source.purposeVocabulary()
        val selected = source.purposes(workId)
        if (gate.mayApply(token)) {
            purposeVocabularyValue = WorkValue(token.target, vocabulary)
            selectedPurposesValue = WorkValue(token.target, selected)
        }
    }

    suspend fun loadAttractionVocabulary(workId: Long) {
        val token = gate.beginLoad(ContextLoadSection.ATTRACTION_VOCABULARY, workId) ?: return
        val vocabulary = source.attractionVocabulary()
        if (gate.mayApply(token)) attractionVocabularyValue = WorkValue(token.target, vocabulary)
    }

    suspend fun loadWorkAttractions(workId: Long) {
        val token = gate.beginLoad(ContextLoadSection.WORK_ATTRACTIONS, workId) ?: return
        val selected = source.workAttractions(workId)
        if (gate.mayApply(token)) {
            selectedWorkAttractionsValue = WorkValue(token.target, selected)
            val vocabulary = attractionVocabularyValue
            if (vocabulary?.workId == workId && gate.isTargetCurrent(vocabulary.target)) {
                attractionVocabularyValue = vocabulary.copy(
                    value = (vocabulary.value + selected).distinctBy { it.id }
                )
            }
        }
    }

    suspend fun loadPageAttractions(workId: Long, mediaId: Long?) {
        val token = gate.beginLoad(ContextLoadSection.PAGE_ATTRACTIONS, workId, mediaId) ?: return
        val selected = if (mediaId == null) emptyList() else source.pageAttractions(mediaId)
        if (gate.mayApply(token)) {
            selectedPageAttractionsValue = PageValue(token.target, selected)
            val vocabulary = attractionVocabularyValue
            if (vocabulary?.workId == workId && gate.isTargetCurrent(vocabulary.target)) {
                attractionVocabularyValue = vocabulary.copy(
                    value = (vocabulary.value + selected).distinctBy { it.id }
                )
            }
        }
    }

    suspend fun loadResponse(workId: Long) {
        val token = gate.beginLoad(ContextLoadSection.RESPONSE, workId) ?: return
        val vocabulary = source.responseVocabulary()
        val selected = source.responses(workId)
        if (gate.mayApply(token)) {
            responseVocabularyValue = WorkValue(token.target, vocabulary)
            selectedResponsesValue = WorkValue(token.target, selected)
        }
    }

    suspend fun loadFocus(workId: Long, mediaId: Long?) {
        val token = gate.beginLoad(ContextLoadSection.FOCUS, workId, mediaId) ?: return
        val selected = if (mediaId == null) emptyList() else source.focuses(mediaId)
        if (gate.mayApply(token)) focusesValue = PageValue(token.target, selected)
    }

    suspend fun setPurpose(workId: Long, value: PurposeVocabularyEntity, selected: Boolean): Boolean =
        mutateWork(workId, { if (selected) source.removePurpose(workId, value.id) else source.addPurpose(workId, value.label) }) {
            loadPurpose(workId)
        }

    suspend fun addPurpose(workId: Long, label: String): Boolean =
        mutateWork(workId, { source.addPurpose(workId, label) }) { loadPurpose(workId) }

    suspend fun setWorkAttraction(workId: Long, value: AttractionVocabularyEntity, selected: Boolean): Boolean =
        mutateWork(workId, {
            if (selected) source.removeWorkAttraction(workId, value.id)
            else source.addWorkAttraction(workId, value.dimension, value.label)
        }) { loadWorkAttractions(workId) }

    suspend fun addWorkAttraction(workId: Long, dimension: String, label: String): Boolean =
        mutateWork(workId, { source.addWorkAttraction(workId, dimension, label) }) { loadWorkAttractions(workId) }

    suspend fun setPageAttraction(workId: Long, mediaId: Long?, value: AttractionVocabularyEntity, selected: Boolean): Boolean =
        mutatePage(workId, mediaId, { id ->
            if (selected) source.removePageAttraction(id, value.id)
            else source.addPageAttraction(id, value.dimension, value.label)
        }) { loadPageAttractions(workId, mediaId) }

    suspend fun addPageAttraction(workId: Long, mediaId: Long?, dimension: String, label: String): Boolean =
        mutatePage(workId, mediaId, { id -> source.addPageAttraction(id, dimension, label) }) {
            loadPageAttractions(workId, mediaId)
        }

    suspend fun setResponse(workId: Long, value: AestheticResponseVocabularyEntity, selected: Boolean): Boolean =
        mutateWork(workId, { if (selected) source.removeResponse(workId, value.id) else source.addResponse(workId, value.label) }) {
            loadResponse(workId)
        }

    suspend fun addResponse(workId: Long, label: String): Boolean =
        mutateWork(workId, { source.addResponse(workId, label) }) { loadResponse(workId) }

    suspend fun addFocus(workId: Long, mediaId: Long?, attractionId: Long?, note: String?): Boolean =
        mutatePage(workId, mediaId, { id -> source.addFocus(id, attractionId, note) }) {
            loadFocus(workId, mediaId)
        }

    suspend fun removeFocus(workId: Long, mediaId: Long?, focus: MediaFocusEntity): Boolean {
        if (mediaId == null || focus.mediaId != mediaId) return false
        return mutatePage(workId, mediaId, { source.removeFocus(focus.id) }) {
            loadFocus(workId, mediaId)
        }
    }

    private suspend fun mutateWork(
        workId: Long,
        operation: suspend () -> Unit,
        refresh: suspend () -> Unit
    ): Boolean {
        val target = gate.captureWork(workId) ?: return false
        operation()
        val stillCurrent = gate.isTargetCurrent(target)
        if (stillCurrent) refresh()
        return stillCurrent && gate.isTargetCurrent(target)
    }

    private suspend fun mutatePage(
        workId: Long,
        mediaId: Long?,
        operation: suspend (Long) -> Unit,
        refresh: suspend () -> Unit
    ): Boolean {
        if (mediaId == null) return false
        val target = gate.captureMedia(workId, mediaId) ?: return false
        operation(mediaId)
        val stillCurrent = gate.isTargetCurrent(target)
        if (stillCurrent) refresh()
        return stillCurrent && gate.isTargetCurrent(target)
    }
}
