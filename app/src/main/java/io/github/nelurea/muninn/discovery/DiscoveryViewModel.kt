package io.github.nelurea.muninn.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import kotlinx.coroutines.launch

class DiscoveryViewModel(
    private val source: DiscoverySource
) : ViewModel() {

    var uiState by mutableStateOf(
        DiscoveryUiState()
    )
        private set

    private var currentPage =
        0

    private var hasNextPage =
        true

    private var isLoading =
        false

    init {
        loadInitial()
    }

    fun loadInitial() {
        if (
            isLoading
        ) {
            return
        }

        currentPage =
            0

        hasNextPage =
            true

        uiState =
            DiscoveryUiState(
                isLoading =
                    true
            )

        loadPage(
            page =
                1,
            replace =
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
                false
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

    private fun loadPage(
        page: Int,
        replace: Boolean
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

        viewModelScope.launch {
            runCatching {
                source.loadLatest(
                    page =
                        page
                )
            }
                .onSuccess {
                        result ->

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