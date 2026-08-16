package io.github.nelurea.muninn.ui.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.nelurea.muninn.discovery.DiscoveryViewModel
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onItemClick: (DiscoveryItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state =
        viewModel.uiState

    val previewState =
        viewModel.previewState

    val saveQueueState =
        viewModel.saveQueueState

    BackHandler(
        enabled =
            previewState.item != null
    ) {
        viewModel.closePreview()
    }

    val gridState =
        rememberLazyGridState()

    var xRefreshToken by rememberSaveable {
        mutableIntStateOf(
            0
        )
    }

    var xLoadMoreToken by rememberSaveable {
        mutableIntStateOf(
            0
        )
    }

    var xIsLoadingMore by rememberSaveable {
        mutableStateOf(
            false
        )
    }

    LaunchedEffect(
        gridState,
        state.items.size,
        state.hasNextPage,
        state.isLoading,
        state.isLoadingMore
    ) {
        snapshotFlow {
            shouldLoadMore(
                gridState =
                    gridState,
                itemCount =
                    state.items.size
            )
        }
            .distinctUntilChanged()
            .collect {
                    shouldLoad ->

                if (
                    !shouldLoad ||
                    state.isLoading ||
                    state.isLoadingMore
                ) {
                    return@collect
                }

                if (
                    state.hasNextPage
                ) {
                    viewModel
                        .loadNextPage()
                } else if (
                    viewModel.sourceId ==
                    DiscoverySourceId.X &&
                    !xIsLoadingMore
                ) {
                    xIsLoadingMore =
                        true

                    xLoadMoreToken +=
                        1
                }
            }
    }

    Box(
        modifier =
            modifier.fillMaxSize()
    ) {
        if (
            viewModel.sourceId ==
            DiscoverySourceId.X
        ) {
            XDiscoveryWebSession(
                mode =
                    viewModel.mode,
                searchQuery =
                    viewModel.searchQuery,
                refreshToken =
                    xRefreshToken,
                loadMoreToken =
                    xLoadMoreToken,
                onLoadMoreStateChange = {
                        loading ->

                    xIsLoadingMore =
                        loading
                },
                onBatchObserved = {
                        batch ->

                    viewModel
                        .notifySourceUpdated(
                            updatedSourceId =
                                DiscoverySourceId.X,
                            updatedMode =
                                batch.mode,
                            updatedQuery =
                                batch.query
                        )
                }
            )
        }

        DiscoveryContent(
            viewModel =
                viewModel,
            gridState =
                gridState,
            xIsLoadingMore =
                xIsLoadingMore,
            onLoadMoreX = {
                if (
                    !xIsLoadingMore
                ) {
                    xIsLoadingMore =
                        true

                    xLoadMoreToken +=
                        1
                }
            },
            onRefreshX = {
                xRefreshToken +=
                    1
            },
            onBack =
                onBack
        )

        if (
            previewState.item != null
        ) {
            ArtworkPreviewOverlay(
                state =
                    previewState,
                onClose = {
                    viewModel
                        .closePreview()
                },
                onRetry = {
                    viewModel
                        .retryPreview()
                },
                onSelect = {
                        mediaIndex ->

                    viewModel
                        .selectMedia(
                            mediaIndex
                        )
                },
                onDeselect = {
                        mediaIndex ->

                    viewModel
                        .deselectMedia(
                            mediaIndex
                        )
                },
                onOpenOriginal = {
                    previewState.item
                        ?.let(
                            onItemClick
                        )
                },
                onTagClick = {
                        tag ->

                    viewModel
                        .searchByTag(
                            tag
                        )

                    if (
                        viewModel.sourceId ==
                        DiscoverySourceId.X
                    ) {
                        xRefreshToken +=
                            1
                    }
                },
                onSaveAll = {
                    viewModel
                        .saveAll()
                },
                onSaveSelected = {
                    viewModel
                        .saveSelected()
                }
            )
        }

        if (
            saveQueueState.message != null
        ) {
            DiscoverySaveStatusBar(
                state =
                    saveQueueState,
                onRetry = {
                    viewModel
                        .retryFailedSave()
                },
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .padding(

                            start = 12.dp,
                            end = 12.dp,
                            bottom =
                                if (
                                    previewState.item != null
                                ) {
                                    112.dp
                                } else {
                                    12.dp
                                }

                        )
            )
        }
    }
}

@Composable
private fun DiscoveryContent(
    viewModel: DiscoveryViewModel,
    gridState: LazyGridState,
    xIsLoadingMore: Boolean,
    onLoadMoreX: () -> Unit,
    onRefreshX: () -> Unit,
    onBack: () -> Unit
) {
    val state =
        viewModel.uiState

    var xEndLoadMoreTriggered by remember {
        mutableStateOf(
            false
        )
    }

    /*
     * Once the current load-more finishes, allow another
     * ordinary upward scroll at the end to trigger the next
     * request.
     */
    LaunchedEffect(
        xIsLoadingMore
    ) {
        if (
            !xIsLoadingMore
        ) {
            xEndLoadMoreTriggered =
                false
        }
    }

    val xBottomScrollConnection =
        remember(
            gridState,
            viewModel.sourceId,
            xIsLoadingMore
        ) {
            object :
                NestedScrollConnection {

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (
                        source !=
                        NestedScrollSource.UserInput
                    ) {
                        return Offset.Zero
                    }

                    if (
                        viewModel.sourceId !=
                        DiscoverySourceId.X
                    ) {
                        return Offset.Zero
                    }

                    if (
                        xIsLoadingMore ||
                        xEndLoadMoreTriggered
                    ) {
                        return Offset.Zero
                    }

                    if (
                        gridState.canScrollForward
                    ) {
                        return Offset.Zero
                    }

                    /*
                     * The visible grid is already at its end.
                     *
                     * A normal continued upward scroll produces
                     * negative unconsumed Y. Treat any such
                     * input as the user's intent to continue
                     * browsing rather than as a separate
                     * pull-to-refresh gesture.
                     */
                    if (
                        available.y <
                        0f
                    ) {
                        xEndLoadMoreTriggered =
                            true

                        onLoadMoreX()
                    }

                    return Offset.Zero
                }
            }
        }

    Column(
        modifier =
            Modifier.fillMaxSize()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 8.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            Button(
                onClick =
                    onBack
            ) {
                Text(
                    "Back"
                )
            }

            Text(
                text =
                    "Discovery",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            DiscoverySourceButton(
                label =
                    "Pixiv",
                selected =
                    viewModel.sourceId ==
                            DiscoverySourceId.PIXIV,
                enabled =
                    viewModel.isSourceAvailable(
                        DiscoverySourceId.PIXIV
                    ),
                onClick = {
                    viewModel.selectSource(
                        DiscoverySourceId.PIXIV
                    )
                }
            )

            DiscoverySourceButton(
                label =
                    "X",
                selected =
                    viewModel.sourceId ==
                            DiscoverySourceId.X,
                enabled =
                    viewModel.isSourceAvailable(
                        DiscoverySourceId.X
                    ),
                onClick = {
                    viewModel.selectSource(
                        DiscoverySourceId.X
                    )
                }
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 8.dp
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            DiscoveryModeButton(
                label =
                    if (
                        viewModel.sourceId ==
                        DiscoverySourceId.X
                    ) {
                        "For You"
                    } else {
                        "Latest"
                    },
                selected =
                    viewModel.mode ==
                            DiscoveryMode.LATEST,
                onClick = {
                    viewModel.selectMode(
                        DiscoveryMode.LATEST
                    )
                }
            )

            DiscoveryModeButton(
                label =
                    if (
                        viewModel.sourceId ==
                        DiscoverySourceId.X
                    ) {
                        "Likes"
                    } else {
                        "Bookmarks"
                    },
                selected =
                    viewModel.mode ==
                            DiscoveryMode.BOOKMARKS,
                onClick = {
                    viewModel.selectMode(
                        DiscoveryMode.BOOKMARKS
                    )
                }
            )

            DiscoveryModeButton(
                label =
                    "Search",
                selected =
                    viewModel.mode ==
                            DiscoveryMode.SEARCH,
                onClick = {
                    viewModel.selectMode(
                        DiscoveryMode.SEARCH
                    )
                }
            )

            if (
                viewModel.sourceId ==
                DiscoverySourceId.X
            ) {
                IconButton(
                    onClick =
                        onRefreshX,
                    enabled =
                        viewModel.mode !=
                                DiscoveryMode.SEARCH ||
                                viewModel.searchQuery
                                    .isNotBlank()
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Refresh,
                        contentDescription =
                            "Refresh"
                    )
                }
            }
        }

        if (
            viewModel.mode ==
            DiscoveryMode.SEARCH
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 8.dp,
                            vertical = 8.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                OutlinedTextField(
                    value =
                        viewModel.searchQuery,
                    onValueChange =
                        viewModel::updateSearchQuery,
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    singleLine =
                        true,
                    label = {
                        Text(
                            when (
                                viewModel.sourceId
                            ) {
                                DiscoverySourceId.PIXIV ->
                                    "Search Pixiv"

                                DiscoverySourceId.X ->
                                    "Search X"
                            }
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Search
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                viewModel
                                    .submitSearch()

                                if (
                                    viewModel.sourceId ==
                                    DiscoverySourceId.X
                                ) {
                                    onRefreshX()
                                }
                            }
                        )
                )

                Button(
                    onClick = {
                        viewModel
                            .submitSearch()

                        if (
                            viewModel.sourceId ==
                            DiscoverySourceId.X
                        ) {
                            onRefreshX()
                        }
                    },
                    enabled =
                        viewModel.searchQuery
                            .isNotBlank()
                ) {
                    Text(
                        "Search"
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(
                        1f
                    )
        ) {
            when {
                state.isLoading &&
                        state.items.isEmpty() -> {

                    Column(
                        modifier =
                            Modifier.fillMaxSize(),
                        verticalArrangement =
                            Arrangement.Center,
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()

                        Text(
                            text =
                                "Loading…",
                            modifier =
                                Modifier.padding(
                                    top = 12.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }
                }

                state.error != null &&
                        state.items.isEmpty() -> {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    24.dp
                                ),
                        verticalArrangement =
                            Arrangement.Center,
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            text =
                                "Couldn’t load Discovery",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            text =
                                state.error,
                            modifier =
                                Modifier.padding(
                                    top = 8.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )

                        Button(
                            onClick = {
                                viewModel.retry()

                                if (
                                    viewModel.sourceId ==
                                    DiscoverySourceId.X
                                ) {
                                    onRefreshX()
                                }
                            },
                            modifier =
                                Modifier.padding(
                                    top = 16.dp
                                )
                        ) {
                            Text(
                                "Retry"
                            )
                        }
                    }
                }

                state.items.isEmpty() -> {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    24.dp
                                ),
                        verticalArrangement =
                            Arrangement.Center,
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            text =
                                if (
                                    viewModel.mode ==
                                    DiscoveryMode.SEARCH
                                ) {
                                    "No results"
                                } else {
                                    "Nothing here yet"
                                },
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            text =
                                if (
                                    viewModel.mode ==
                                    DiscoveryMode.SEARCH
                                ) {
                                    "Try another search."
                                } else {
                                    "No items are available for this view."
                                },
                            modifier =
                                Modifier.padding(
                                    top = 6.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns =
                            GridCells.Fixed(
                                2
                            ),
                        state =
                            gridState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(
                                    xBottomScrollConnection
                                ),
                        contentPadding =
                            PaddingValues(
                                8.dp
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        items(
                            items =
                                state.items,
                            key = {
                                "${it.source}:${it.sourceItemId}"
                            }
                        ) {
                                item ->

                            DiscoveryGridItem(
                                item =
                                    item,
                                onClick = {
                                    viewModel
                                        .openPreview(
                                            item
                                        )
                                }
                            )
                        }

                        if (
                            state.isLoadingMore ||
                            xIsLoadingMore
                        ) {
                            item(
                                span = {
                                    GridItemSpan(
                                        maxLineSpan
                                    )
                                }
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                24.dp
                                            ),
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            8.dp
                                        )
                                ) {
                                    CircularProgressIndicator()

                                    Text(
                                        text =
                                            "Loading more…",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                            }
                        }

                        if (
                            state.error != null &&
                            state.items.isNotEmpty()
                        ) {
                            item(
                                span = {
                                    GridItemSpan(
                                        maxLineSpan
                                    )
                                }
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                16.dp
                                            ),
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text =
                                            "Couldn’t load more items",
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    Text(
                                        text =
                                            state.error,
                                        modifier =
                                            Modifier.padding(
                                                top = 4.dp
                                            )
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.retry()

                                            if (
                                                viewModel.sourceId ==
                                                DiscoverySourceId.X
                                            ) {
                                                onRefreshX()
                                            }
                                        },
                                        modifier =
                                            Modifier.padding(
                                                top = 8.dp
                                            )
                                    ) {
                                        Text(
                                            "Retry"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
