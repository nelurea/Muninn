package io.github.nelurea.muninn.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nelurea.muninn.capture.discovery.DiscoveryArtworkSaveMode
import io.github.nelurea.muninn.capture.discovery.PixivDiscoverySaveUseCase
import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import kotlinx.coroutines.launch

class DiscoveryViewModel(
    private val source: DiscoverySource,
    private val previewSource: ArtworkPreviewSource,
    private val saveUseCase: PixivDiscoverySaveUseCase,
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
        loadInitial()
    }

    fun selectMode(
        newMode: DiscoveryMode
    ) {
        if (
            mode == newMode
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

    fun openPreview(
        item: DiscoveryItem
    ) {
        previewGeneration += 1

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
                                null,
                            isSaving =
                                false,
                            saveMessage =
                                null,
                            saveError =
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
                                    ?: "Failed to load artwork preview.",
                            isSaving =
                                false,
                            saveMessage =
                                null,
                            saveError =
                                null
                        )
                }
        }
    }

    fun closePreview() {
        previewGeneration += 1

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
                            mediaIndex,
                saveMessage =
                    null,
                saveError =
                    null
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
                            mediaIndex,
                saveMessage =
                    null,
                saveError =
                    null
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
        if (
            previewState.isSaving
        ) {
            return
        }

        val preview =
            previewState.preview
                ?: return

        val selectedMediaIndices =
            previewState.selectedMediaIndices

        if (
            saveMode ==
            DiscoveryArtworkSaveMode.SELECTED &&
            selectedMediaIndices.isEmpty()
        ) {
            previewState =
                previewState.copy(
                    saveMessage =
                        null,
                    saveError =
                        "Select at least one page first."
                )

            return
        }

        val requestGeneration =
            previewGeneration

        val discoveryMode =
            mode.name

        val discoveryQuery =
            searchQuery
                .takeIf {
                    mode ==
                            DiscoveryMode.SEARCH &&
                            it.isNotBlank()
                }

        previewState =
            previewState.copy(
                isSaving =
                    true,
                saveMessage =
                    null,
                saveError =
                    null
            )

        viewModelScope.launch {
            val result =
                saveUseCase.save(
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

            if (
                requestGeneration !=
                previewGeneration
            ) {
                return@launch
            }

            when (
                result
            ) {
                is SaveCaptureResult.Success -> {
                    previewState =
                        previewState.copy(
                            isSaving =
                                false,
                            saveMessage =
                                if (
                                    result.mediaCount == 1
                                ) {
                                    "Saved 1 page."
                                } else {
                                    "Saved ${result.mediaCount} pages."
                                },
                            saveError =
                                null
                        )
                }

                is SaveCaptureResult.Failure -> {
                    previewState =
                        previewState.copy(
                            isSaving =
                                false,
                            saveMessage =
                                null,
                            saveError =
                                result.errors
                                    .joinToString(
                                        separator = "\n"
                                    )
                        )
                }
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

        generation += 1

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
                generation
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
                generation
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

    private fun clearResults() {
        generation += 1

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
        requestGeneration: Int
    ) {
        if (
            isLoading
        ) {
            return
        }

        isLoading =
            true

        uiState =
            uiState.copy(
                isLoading =
                    replace,
                isLoadingMore =
                    !replace,
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
        null,

    val isSaving: Boolean =
        false,

    val saveMessage: String? =
        null,

    val saveError: String? =
        null
)