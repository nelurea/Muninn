package io.github.nelurea.muninn.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nelurea.muninn.capture.discovery.DiscoveryArtworkSaveMode
import io.github.nelurea.muninn.capture.discovery.DiscoverySaveCoordinator
import io.github.nelurea.muninn.capture.discovery.DiscoverySaveEnqueueResult
import io.github.nelurea.muninn.capture.discovery.DiscoverySaveRequestKey
import io.github.nelurea.muninn.capture.discovery.DiscoverySaveStatus
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DiscoveryViewModel(
    private val sources: Map<
            DiscoverySourceId,
            DiscoverySource
            >,
    private val previewSources: Map<
            DiscoverySourceId,
            ArtworkPreviewSource
            >,
    private val saveCoordinator:
    DiscoverySaveCoordinator,
    initialSource: DiscoverySourceId =
        DiscoverySourceId.PIXIV,
    initialMode: DiscoveryMode =
        DiscoveryMode.LATEST
) : ViewModel() {

    var uiState by mutableStateOf(
        DiscoveryUiState()
    )
        private set

    var previewState by mutableStateOf(
        ArtworkPreviewUiState()
    )
        private set

    var saveQueueState by mutableStateOf(
        DiscoverySaveQueueUiState()
    )
        private set

    var sourceId by mutableStateOf(
        initialSource
    )
        private set

    var mode by mutableStateOf(
        initialMode
    )
        private set

    var searchQuery by mutableStateOf(
        ""
    )
        private set

    private var currentPage =
        0

    private var hasNextPage =
        true

    private var isLoading =
        false

    private var generation =
        0

    private var previewGeneration =
        0

    init {
        require(
            sources.containsKey(
                initialSource
            )
        ) {
            "No DiscoverySource is registered for $initialSource."
        }

        require(
            previewSources.containsKey(
                initialSource
            )
        ) {
            "No ArtworkPreviewSource is registered for $initialSource."
        }

        viewModelScope.launch {
            saveCoordinator
                .states
                .collectLatest {
                        states ->

                    val activeCount =
                        states
                            .values
                            .count {
                                it.status ==
                                        DiscoverySaveStatus.QUEUED ||
                                        it.status ==
                                        DiscoverySaveStatus.SAVING
                            }

                    val failed =
                        states
                            .values
                            .lastOrNull {
                                it.status ==
                                        DiscoverySaveStatus.FAILED
                            }

                    val saved =
                        states
                            .values
                            .lastOrNull {
                                it.status ==
                                        DiscoverySaveStatus.SAVED
                            }

                    saveQueueState =
                        when {
                            activeCount > 0 -> {
                                DiscoverySaveQueueUiState(
                                    activeCount =
                                        activeCount,
                                    message =
                                        if (
                                            activeCount == 1
                                        ) {
                                            "Saving in background…"
                                        } else {
                                            "Saving $activeCount works in background…"
                                        }
                                )
                            }

                            failed != null -> {
                                DiscoverySaveQueueUiState(
                                    message =
                                        failed.error
                                            ?: "Background save failed.",
                                    failedRequestKey =
                                        failed.key
                                )
                            }

                            saved != null -> {
                                val mediaCount =
                                    saved.mediaCount
                                        ?: 0

                                DiscoverySaveQueueUiState(
                                    message =
                                        when {
                                            mediaCount == 0 ->
                                                "Already saved."

                                            mediaCount == 1 ->
                                                "Saved 1 page."

                                            else ->
                                                "Saved $mediaCount pages."
                                        }
                                )
                            }

                            else -> {
                                DiscoverySaveQueueUiState()
                            }
                        }
                }
        }

        loadInitial()
    }

    fun isSourceAvailable(
        candidate: DiscoverySourceId
    ): Boolean {
        return sources.containsKey(
            candidate
        ) &&
                previewSources.containsKey(
                    candidate
                )
    }

    fun selectSource(
        newSourceId: DiscoverySourceId
    ) {
        if (
            sourceId ==
            newSourceId
        ) {
            return
        }

        if (
            !isSourceAvailable(
                newSourceId
            )
        ) {
            return
        }

        closePreview()

        sourceId =
            newSourceId

        mode =
            DiscoveryMode.LATEST

        searchQuery =
            ""

        loadInitial()
    }

    fun selectMode(
        newMode: DiscoveryMode
    ) {
        if (
            mode ==
            newMode
        ) {
            return
        }

        closePreview()

        mode =
            newMode

        if (
            newMode ==
            DiscoveryMode.SEARCH &&
            searchQuery.isBlank()
        ) {
            clearResults()

            return
        }

        loadInitial()
    }

    fun updateSearchQuery(
        newQuery: String
    ) {
        searchQuery =
            newQuery
    }

    fun submitSearch() {
        if (
            mode !=
            DiscoveryMode.SEARCH
        ) {
            return
        }

        val normalizedQuery =
            searchQuery.trim()

        searchQuery =
            normalizedQuery

        closePreview()

        if (
            normalizedQuery.isBlank()
        ) {
            clearResults()

            return
        }

        loadInitial()
    }

    fun searchByTag(
        tag: String
    ) {
        val normalizedTag =
            tag.trim()

        if (
            normalizedTag.isBlank()
        ) {
            return
        }

        closePreview()

        mode =
            DiscoveryMode.SEARCH

        searchQuery =
            normalizedTag

        loadInitial()
    }

    /*
     * Called when a source observes new data independently
     * from DiscoverySource.load().
     *
     * X uses this because its hidden authenticated WebView
     * receives timeline batches asynchronously.
     */
    fun notifySourceUpdated(
        updatedSourceId: DiscoverySourceId,
        updatedMode: DiscoveryMode,
        updatedQuery: String? = null
    ) {
        if (
            sourceId !=
            updatedSourceId
        ) {
            return
        }

        if (
            mode !=
            updatedMode
        ) {
            return
        }

        if (
            mode ==
            DiscoveryMode.SEARCH
        ) {
            val currentQuery =
                searchQuery
                    .trim()

            val observedQuery =
                updatedQuery
                    ?.trim()
                    .orEmpty()

            if (
                currentQuery !=
                observedQuery
            ) {
                return
            }
        }

        refreshCurrentSource()
    }

    fun openPreview(
        item: DiscoveryItem
    ) {
        previewGeneration +=
            1

        val requestGeneration =
            previewGeneration

        previewState =
            ArtworkPreviewUiState(
                item =
                    item,
                isLoading =
                    true
            )

        viewModelScope.launch {
            runCatching {
                val previewSource =
                    previewSources[
                        item.source
                    ]
                        ?: error(
                            "No ArtworkPreviewSource is registered for ${item.source}."
                        )

                previewSource.load(
                    item
                )
            }
                .onSuccess {
                        preview ->

                    if (
                        requestGeneration !=
                        previewGeneration
                    ) {
                        return@onSuccess
                    }

                    previewState =
                        previewState.copy(
                            preview =
                                preview,
                            selectedMediaIndices =
                                emptySet(),
                            isLoading =
                                false,
                            error =
                                null
                        )
                }
                .onFailure {
                        error ->

                    if (
                        requestGeneration !=
                        previewGeneration
                    ) {
                        return@onFailure
                    }

                    previewState =
                        previewState.copy(
                            preview =
                                null,
                            selectedMediaIndices =
                                emptySet(),
                            isLoading =
                                false,
                            error =
                                error.message
                                    ?: "Failed to load artwork preview."
                        )
                }
        }
    }

    fun closePreview() {
        previewGeneration +=
            1

        previewState =
            ArtworkPreviewUiState()
    }

    fun retryPreview() {
        val item =
            previewState.item
                ?: return

        openPreview(
            item
        )
    }

    fun selectMedia(
        mediaIndex: Int
    ) {
        val preview =
            previewState.preview
                ?: return

        if (
            preview.media.none {
                it.mediaIndex ==
                        mediaIndex
            }
        ) {
            return
        }

        if (
            mediaIndex in
            previewState.selectedMediaIndices
        ) {
            return
        }

        previewState =
            previewState.copy(
                selectedMediaIndices =
                    previewState
                        .selectedMediaIndices +
                            mediaIndex
            )
    }

    fun deselectMedia(
        mediaIndex: Int
    ) {
        if (
            mediaIndex !in
            previewState.selectedMediaIndices
        ) {
            return
        }

        previewState =
            previewState.copy(
                selectedMediaIndices =
                    previewState
                        .selectedMediaIndices -
                            mediaIndex
            )
    }

    fun saveAll() {
        savePreview(
            saveMode =
                DiscoveryArtworkSaveMode.ALL
        )
    }

    fun saveSelected() {
        savePreview(
            saveMode =
                DiscoveryArtworkSaveMode.SELECTED
        )
    }

    private fun savePreview(
        saveMode: DiscoveryArtworkSaveMode
    ) {
        val preview =
            previewState.preview
                ?: return

        val selectedMediaIndices =
            previewState
                .selectedMediaIndices

        if (
            saveMode ==
            DiscoveryArtworkSaveMode.SELECTED &&
            selectedMediaIndices.isEmpty()
        ) {
            return
        }

        val discoveryMode =
            mode.name

        val discoveryQuery =
            searchQuery
                .takeIf {
                    mode ==
                            DiscoveryMode.SEARCH &&
                            it.isNotBlank()
                }

        when (
            val result =
                saveCoordinator.enqueue(
                    preview =
                        preview,
                    selectedMediaIndices =
                        selectedMediaIndices,
                    mode =
                        saveMode,
                    discoveryMode =
                        discoveryMode,
                    discoveryQuery =
                        discoveryQuery
                )
        ) {
            is DiscoverySaveEnqueueResult.Accepted -> {
                /*
                 * Save state is now owned entirely by
                 * DiscoverySaveCoordinator.
                 */
            }

            is DiscoverySaveEnqueueResult.AlreadyRunning -> {
                saveQueueState =
                    saveQueueState.copy(
                        message =
                            "Already saving in background."
                    )
            }

            is DiscoverySaveEnqueueResult.Failure -> {
                saveQueueState =
                    DiscoverySaveQueueUiState(
                        message =
                            result.error
                    )
            }
        }
    }

    fun retryFailedSave() {
        val key =
            saveQueueState
                .failedRequestKey
                ?: return

        when (
            val result =
                saveCoordinator.retry(
                    key
                )
        ) {
            is DiscoverySaveEnqueueResult.Accepted -> {
                saveQueueState =
                    saveQueueState.copy(
                        message =
                            "Retrying save…",
                        failedRequestKey =
                            null
                    )
            }

            is DiscoverySaveEnqueueResult.AlreadyRunning -> {
                saveQueueState =
                    saveQueueState.copy(
                        message =
                            "Already saving in background.",
                        failedRequestKey =
                            null
                    )
            }

            is DiscoverySaveEnqueueResult.Failure -> {
                saveQueueState =
                    saveQueueState.copy(
                        message =
                            result.error
                    )
            }
        }
    }

    fun loadInitial() {
        if (
            mode ==
            DiscoveryMode.SEARCH &&
            searchQuery.isBlank()
        ) {
            clearResults()

            return
        }

        generation +=
            1

        currentPage =
            0

        hasNextPage =
            true

        isLoading =
            false

        uiState =
            DiscoveryUiState(
                isLoading =
                    true
            )

        loadPage(
            page =
                1,
            replace =
                true,
            requestGeneration =
                generation,
            showLoading =
                true
        )
    }

    fun loadNextPage() {
        if (
            isLoading ||
            !hasNextPage
        ) {
            return
        }

        loadPage(
            page =
                currentPage + 1,
            replace =
                false,
            requestGeneration =
                generation,
            showLoading =
                true
        )
    }

    fun retry() {
        if (
            uiState.items.isEmpty()
        ) {
            loadInitial()
        } else {
            loadNextPage()
        }
    }

    private fun refreshCurrentSource() {
        if (
            mode ==
            DiscoveryMode.SEARCH &&
            searchQuery.isBlank()
        ) {
            return
        }

        generation +=
            1

        currentPage =
            0

        hasNextPage =
            true

        isLoading =
            false

        loadPage(
            page =
                1,
            replace =
                true,
            requestGeneration =
                generation,
            showLoading =
                false
        )
    }

    private fun clearResults() {
        generation +=
            1

        currentPage =
            0

        hasNextPage =
            true

        isLoading =
            false

        uiState =
            DiscoveryUiState()
    }

    private fun loadPage(
        page: Int,
        replace: Boolean,
        requestGeneration: Int,
        showLoading: Boolean
    ) {
        if (
            isLoading
        ) {
            return
        }

        val source =
            sources[
                sourceId
            ]
                ?: run {
                    uiState =
                        DiscoveryUiState(
                            error =
                                "No Discovery source is available for $sourceId."
                        )

                    return
                }

        isLoading =
            true

        uiState =
            uiState.copy(
                isLoading =
                    replace &&
                            showLoading,
                isLoadingMore =
                    !replace &&
                            showLoading,
                error =
                    null
            )

        val requestedMode =
            mode

        val requestedQuery =
            if (
                requestedMode ==
                DiscoveryMode.SEARCH
            ) {
                searchQuery
            } else {
                null
            }

        viewModelScope.launch {
            runCatching {
                source.load(
                    mode =
                        requestedMode,
                    page =
                        page,
                    query =
                        requestedQuery
                )
            }
                .onSuccess {
                        result ->

                    if (
                        requestGeneration !=
                        generation
                    ) {
                        return@onSuccess
                    }

                    currentPage =
                        result.page

                    hasNextPage =
                        result.hasNextPage

                    val updatedItems =
                        if (
                            replace
                        ) {
                            result.items
                        } else {
                            mergeItems(
                                current =
                                    uiState.items,
                                additional =
                                    result.items
                            )
                        }

                    uiState =
                        uiState.copy(
                            items =
                                updatedItems,
                            isLoading =
                                false,
                            isLoadingMore =
                                false,
                            hasNextPage =
                                hasNextPage,
                            error =
                                null
                        )

                    isLoading =
                        false
                }
                .onFailure {
                        error ->

                    if (
                        requestGeneration !=
                        generation
                    ) {
                        return@onFailure
                    }

                    uiState =
                        uiState.copy(
                            isLoading =
                                false,
                            isLoadingMore =
                                false,
                            error =
                                error.message
                                    ?: "Failed to load discovery items."
                        )

                    isLoading =
                        false
                }
        }
    }

    private fun mergeItems(
        current: List<DiscoveryItem>,
        additional: List<DiscoveryItem>
    ): List<DiscoveryItem> {
        val existingKeys =
            current
                .mapTo(
                    mutableSetOf()
                ) {
                    ItemKey(
                        source =
                            it.source,
                        sourceItemId =
                            it.sourceItemId
                    )
                }

        val newItems =
            additional.filter {
                existingKeys.add(
                    ItemKey(
                        source =
                            it.source,
                        sourceItemId =
                            it.sourceItemId
                    )
                )
            }

        return current +
                newItems
    }

    private data class ItemKey(
        val source: DiscoverySourceId,
        val sourceItemId: String
    )
}

data class DiscoveryUiState(
    val items: List<DiscoveryItem> =
        emptyList(),

    val isLoading: Boolean =
        false,

    val isLoadingMore: Boolean =
        false,

    val hasNextPage: Boolean =
        true,

    val error: String? =
        null
)

data class ArtworkPreviewUiState(
    val item: DiscoveryItem? =
        null,

    val preview: ArtworkPreview? =
        null,

    val selectedMediaIndices: Set<Int> =
        emptySet(),

    val isLoading: Boolean =
        false,

    val error: String? =
        null
)

data class DiscoverySaveQueueUiState(
    val activeCount: Int =
        0,

    val message: String? =
        null,

    val failedRequestKey:
    DiscoverySaveRequestKey? =
        null
)
