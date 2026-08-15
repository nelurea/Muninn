package io.github.nelurea.muninn.ui.discovery

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.discovery.ArtworkPreviewUiState
import io.github.nelurea.muninn.discovery.DiscoveryViewModel
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.ContentRestriction
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

    val gridState =
        rememberLazyGridState()

    var xRefreshToken by rememberSaveable {
        mutableStateOf(
            0
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
                    shouldLoad &&
                    state.hasNextPage &&
                    !state.isLoading &&
                    !state.isLoadingMore
                ) {
                    viewModel
                        .loadNextPage()
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
                    val item =
                        previewState.item

                    if (
                        item != null
                    ) {
                        onItemClick(
                            item
                        )
                    }
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
    }
}

@Composable
private fun DiscoveryContent(
    viewModel: DiscoveryViewModel,
    gridState: LazyGridState,
    onRefreshX: () -> Unit,
    onBack: () -> Unit
) {
    val state =
        viewModel.uiState

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
                )
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
                OutlinedButton(
                    onClick =
                        onRefreshX,
                    enabled =
                        viewModel.mode !=
                                DiscoveryMode.SEARCH ||
                                viewModel.searchQuery
                                    .isNotBlank()
                ) {
                    Text(
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

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                                state.error
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

                else -> {
                    LazyVerticalGrid(
                        columns =
                            GridCells.Fixed(
                                2
                            ),
                        state =
                            gridState,
                        modifier =
                            Modifier.fillMaxSize(),
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
                            state.isLoadingMore
                        ) {
                            item(
                                span = {
                                    GridItemSpan(
                                        maxLineSpan
                                    )
                                }
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                24.dp
                                            ),
                                    contentAlignment =
                                        Alignment.Center
                                ) {
                                    CircularProgressIndicator()
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
                                            state.error
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

@Composable
private fun ArtworkPreviewOverlay(
    state: ArtworkPreviewUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (Int) -> Unit,
    onDeselect: (Int) -> Unit,
    onOpenOriginal: () -> Unit,
    onTagClick: (String) -> Unit,
    onSaveAll: () -> Unit,
    onSaveSelected: () -> Unit
) {
    var controlsVisible by rememberSaveable {
        mutableStateOf(
            true
        )
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),
        color =
            Color.Black.copy(
                alpha = 0.94f
            )
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
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
                            state.error,
                        color =
                            Color.White
                    )

                    Button(
                        onClick =
                            onRetry,
                        modifier =
                            Modifier.padding(
                                top = 16.dp
                            )
                    ) {
                        Text(
                            "Retry"
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onClose,
                        modifier =
                            Modifier.padding(
                                top = 8.dp
                            )
                    ) {
                        Text(
                            "Close"
                        )
                    }
                }
            }

            state.preview != null -> {
                val preview =
                    state.preview

                val pagerState =
                    rememberPagerState(
                        pageCount = {
                            preview.media.size
                        }
                    )

                Box(
                    modifier =
                        Modifier.fillMaxSize()
                ) {
                    HorizontalPager(
                        state =
                            pagerState,
                        modifier =
                            Modifier.fillMaxSize(),
                        beyondViewportPageCount =
                            1,
                        userScrollEnabled =
                            !state.isSaving
                    ) {
                            page ->

                        val media =
                            preview.media[
                                page
                            ]

                        ArtworkPagerPage(
                            media =
                                media,
                            source =
                                preview.source,
                            selected =
                                media.mediaIndex in
                                        state.selectedMediaIndices,
                            controlsVisible =
                                controlsVisible,
                            gesturesEnabled =
                                !state.isSaving,
                            onSelect =
                                onSelect,
                            onDeselect =
                                onDeselect,
                            onToggleControls = {
                                controlsVisible =
                                    !controlsVisible
                            }
                        )
                    }

                    if (
                        controlsVisible
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.TopCenter
                                    )
                                    .fillMaxWidth()
                                    .padding(
                                        12.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            Button(
                                onClick =
                                    onClose,
                                enabled =
                                    !state.isSaving
                            ) {
                                Text(
                                    "Close"
                                )
                            }

                            OutlinedButton(
                                onClick =
                                    onOpenOriginal,
                                enabled =
                                    !state.isSaving
                            ) {
                                Text(
                                    sourceLabel(
                                        preview.source
                                    )
                                )
                            }

                            Text(
                                text =
                                    "${pagerState.currentPage + 1} / ${preview.media.size}",
                                color =
                                    Color.White,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )
                        }

                        val currentMedia =
                            preview.media[
                                pagerState.currentPage
                            ]

                        val selected =
                            currentMedia.mediaIndex in
                                    state.selectedMediaIndices

                        ArtworkPreviewInfoPanel(
                            preview =
                                preview,
                            selected =
                                selected,
                            selectedCount =
                                state
                                    .selectedMediaIndices
                                    .size,
                            isSaving =
                                state.isSaving,
                            saveMessage =
                                state.saveMessage,
                            saveError =
                                state.saveError,
                            onTagClick =
                                onTagClick,
                            onSaveAll =
                                onSaveAll,
                            onSaveSelected =
                                onSaveSelected,
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.BottomCenter
                                    )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkPreviewInfoPanel(
    preview: ArtworkPreview,
    selected: Boolean,
    selectedCount: Int,
    isSaving: Boolean,
    saveMessage: String?,
    saveError: String?,
    onTagClick: (String) -> Unit,
    onSaveAll: () -> Unit,
    onSaveSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context =
        LocalContext.current

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    12.dp
                ),
        color =
            Color.Black.copy(
                alpha = 0.76f
            ),
        shape =
            RoundedCornerShape(
                14.dp
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    12.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    7.dp
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                preview.creatorAvatarUrl
                    ?.let {
                            avatarUrl ->

                        val avatarRequest =
                            ImageRequest
                                .Builder(
                                    context
                                )
                                .data(
                                    avatarUrl
                                )
                                .addHeader(
                                    "Referer",
                                    sourceReferer(
                                        preview.source
                                    )
                                )
                                .build()

                        AsyncImage(
                            model =
                                avatarRequest,
                            contentDescription =
                                preview.creator
                                    ?.name,
                            modifier =
                                Modifier
                                    .size(
                                        36.dp
                                    )
                                    .clip(
                                        CircleShape
                                    ),
                            contentScale =
                                ContentScale.Crop
                        )
                    }

                preview.creator
                    ?.name
                    ?.let {
                            creatorName ->

                        Text(
                            text =
                                creatorName,
                            color =
                                Color.White,
                            style =
                                MaterialTheme
                                    .typography
                                    .labelLarge,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }
            }

            preview.title
                ?.let {
                        title ->

                    Text(
                        text =
                            title,
                        color =
                            Color.White,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines =
                            2
                    )
                }

            preview.caption
                ?.let {
                        caption ->

                    ArtworkCaption(
                        caption =
                            caption,
                        sourceItemId =
                            preview.sourceItemId
                    )
                }

            if (
                preview.tags.isNotEmpty()
            ) {
                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    items(
                        items =
                            preview.tags,
                        key = {
                            it
                        }
                    ) {
                            tag ->

                        Surface(
                            modifier =
                                Modifier.clickable(
                                    enabled =
                                        !isSaving
                                ) {
                                    onTagClick(
                                        tag
                                    )
                                },
                            color =
                                Color.White.copy(
                                    alpha = 0.12f
                                ),
                            shape =
                                RoundedCornerShape(
                                    999.dp
                                )
                        ) {
                            Text(
                                text =
                                    "#$tag",
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            10.dp,
                                        vertical =
                                            5.dp
                                    ),
                                color =
                                    Color.White,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelMedium
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    text =
                        if (
                            selected
                        ) {
                            "Selected · ↑ remove"
                        } else {
                            "↓ select"
                        },
                    color =
                        if (
                            selected
                        ) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            Color.White
                        },
                    fontWeight =
                        if (
                            selected
                        ) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                )

                Text(
                    text =
                        "$selectedCount selected",
                    color =
                        Color.White.copy(
                            alpha = 0.7f
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }

            saveMessage
                ?.let {
                        message ->

                    Text(
                        text =
                            message,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )
                }

            saveError
                ?.let {
                        error ->

                    Text(
                        text =
                            error,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )
                }

            if (
                preview.media.size == 1
            ) {
                Button(
                    onClick =
                        onSaveAll,
                    enabled =
                        !isSaving,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            isSaving
                        ) {
                            "Saving..."
                        } else {
                            "Save"
                        }
                    )
                }
            } else {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    Button(
                        onClick =
                            onSaveAll,
                        enabled =
                            !isSaving,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            if (
                                isSaving
                            ) {
                                "Saving..."
                            } else {
                                "Save all"
                            }
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onSaveSelected,
                        enabled =
                            !isSaving &&
                                    selectedCount > 0,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            "Save selected"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkCaption(
    caption: String,
    sourceItemId: String
) {
    val plainCaption =
        HtmlCompat
            .fromHtml(
                caption,
                HtmlCompat
                    .FROM_HTML_MODE_LEGACY
            )
            .toString()
            .trim()

    if (
        plainCaption.isBlank()
    ) {
        return
    }

    var expanded by remember(
        sourceItemId
    ) {
        mutableStateOf(
            false
        )
    }

    var overflowed by remember(
        sourceItemId
    ) {
        mutableStateOf(
            false
        )
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Text(
            text =
                plainCaption,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled =
                            expanded ||
                                    overflowed
                    ) {
                        expanded =
                            !expanded
                    },
            color =
                Color.White.copy(
                    alpha = 0.82f
                ),
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            maxLines =
                if (
                    expanded
                ) {
                    Int.MAX_VALUE
                } else {
                    3
                },
            overflow =
                TextOverflow.Ellipsis,
            onTextLayout = {
                    result ->

                if (
                    !expanded
                ) {
                    overflowed =
                        result.hasVisualOverflow
                }
            }
        )

        if (
            expanded ||
            overflowed
        ) {
            Text(
                text =
                    if (
                        expanded
                    ) {
                        "折りたたむ"
                    } else {
                        "続きを読む"
                    },
                modifier =
                    Modifier
                        .align(
                            Alignment.End
                        )
                        .padding(
                            top = 3.dp
                        )
                        .clickable {
                            expanded =
                                !expanded
                        },
                color =
                    Color.White.copy(
                        alpha = 0.55f
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelMedium
            )
        }
    }
}

@Composable
private fun ArtworkPagerPage(
    media: ArtworkPreviewMedia,
    source: DiscoverySourceId,
    selected: Boolean,
    controlsVisible: Boolean,
    gesturesEnabled: Boolean,
    onSelect: (Int) -> Unit,
    onDeselect: (Int) -> Unit,
    onToggleControls: () -> Unit
) {
    val context =
        LocalContext.current

    val imageRequest =
        ImageRequest
            .Builder(
                context
            )
            .data(
                media.previewUrl
            )
            .addHeader(
                "Referer",
                sourceReferer(
                    source
                )
            )
            .build()

    var verticalDragTotal by remember(
        media.mediaIndex
    ) {
        mutableFloatStateOf(
            0f
        )
    }

    val swipeThreshold =
        with(
            androidx.compose.ui.platform.LocalDensity.current
        ) {
            36.dp.toPx()
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(
                    media.mediaIndex,
                    gesturesEnabled
                ) {
                    if (
                        !gesturesEnabled
                    ) {
                        return@pointerInput
                    }

                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalDragTotal =
                                0f
                        },
                        onVerticalDrag = {
                                change,
                                dragAmount ->

                            verticalDragTotal +=
                                dragAmount

                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                verticalDragTotal >=
                                        swipeThreshold -> {

                                    onSelect(
                                        media.mediaIndex
                                    )
                                }

                                verticalDragTotal <=
                                        -swipeThreshold -> {

                                    onDeselect(
                                        media.mediaIndex
                                    )
                                }
                            }

                            verticalDragTotal =
                                0f
                        },
                        onDragCancel = {
                            verticalDragTotal =
                                0f
                        }
                    )
                }
                .pointerInput(
                    media.mediaIndex,
                    gesturesEnabled
                ) {
                    if (
                        !gesturesEnabled
                    ) {
                        return@pointerInput
                    }

                    detectTapGestures(
                        onTap = {
                            onToggleControls()
                        }
                    )
                },
        contentAlignment =
            Alignment.Center
    ) {
        AsyncImage(
            model =
                imageRequest,
            contentDescription =
                "Page ${media.mediaIndex + 1}",
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        vertical = 56.dp
                    ),
            contentScale =
                ContentScale.Fit,
            onError = {
                Log.e(
                    "Muninn/ArtworkPreview",
                    "Failed to load ${media.previewUrl}: " +
                            it.result.throwable
                )
            }
        )

        if (
            selected &&
            !controlsVisible
        ) {
            Surface(
                modifier =
                    Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(
                            12.dp
                        ),
                shape =
                    RoundedCornerShape(
                        999.dp
                    ),
                color =
                    MaterialTheme
                        .colorScheme
                        .primary
                        .copy(
                            alpha = 0.88f
                        )
            ) {
                Text(
                    text =
                        "✓",
                    modifier =
                        Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DiscoverySourceButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (
        selected
    ) {
        Button(
            onClick = {},
            enabled =
                enabled
        ) {
            Text(
                label
            )
        }
    } else {
        OutlinedButton(
            onClick =
                onClick,
            enabled =
                enabled
        ) {
            Text(
                label
            )
        }
    }
}

@Composable
private fun DiscoveryModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (
        selected
    ) {
        Button(
            onClick = {}
        ) {
            Text(
                label
            )
        }
    } else {
        OutlinedButton(
            onClick =
                onClick
        ) {
            Text(
                label
            )
        }
    }
}

@Composable
private fun DiscoveryBadge(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier,
        shape =
            RoundedCornerShape(
                6.dp
            ),
        color =
            backgroundColor
    ) {
        Text(
            text =
                text,
            modifier =
                Modifier.padding(
                    horizontal = 7.dp,
                    vertical = 3.dp
                ),
            color =
                Color.White,
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun DiscoveryGridItem(
    item: DiscoveryItem,
    onClick: () -> Unit
) {
    val context =
        LocalContext.current

    val imageRequest =
        ImageRequest
            .Builder(
                context
            )
            .data(
                item.previewImageUrl
            )
            .addHeader(
                "Referer",
                sourceReferer(
                    item.source
                )
            )
            .build()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick =
                        onClick
                )
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        1f
                    )
        ) {
            AsyncImage(
                model =
                    imageRequest,
                contentDescription =
                    item.title,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop,
                onError = {
                    Log.e(
                        "Muninn/DiscoveryImage",
                        "Failed to load ${item.previewImageUrl}: " +
                                it.result.throwable
                    )
                }
            )

            when (
                item.restriction
            ) {
                ContentRestriction.R18 -> {
                    DiscoveryBadge(
                        text =
                            "R-18",
                        backgroundColor =
                            Color(
                                0xFFE91E63
                            ),
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopStart
                                )
                                .padding(
                                    6.dp
                                )
                    )
                }

                ContentRestriction.R18G -> {
                    DiscoveryBadge(
                        text =
                            "R-18G",
                        backgroundColor =
                            Color(
                                0xFFD32F2F
                            ),
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopStart
                                )
                                .padding(
                                    6.dp
                                )
                    )
                }

                else -> {
                }
            }

            if (
                item.mediaCount > 1
            ) {
                DiscoveryBadge(
                    text =
                        "▣ ${item.mediaCount}",
                    backgroundColor =
                        Color.Black.copy(
                            alpha = 0.72f
                        ),
                    modifier =
                        Modifier
                            .align(
                                Alignment.TopEnd
                            )
                            .padding(
                                6.dp
                            )
                )
            }
        }

        item.title
            ?.let {
                    title ->

                Text(
                    text =
                        title,
                    modifier =
                        Modifier.padding(
                            top = 6.dp
                        ),
                    maxLines =
                        2
                )
            }

        item.creator
            ?.name
            ?.let {
                    creatorName ->

                Text(
                    text =
                        creatorName,
                    modifier =
                        Modifier.padding(
                            top = 2.dp
                        ),
                    maxLines =
                        1
                )
            }
    }
}

private fun sourceLabel(
    source: DiscoverySourceId
): String {
    return when (
        source
    ) {
        DiscoverySourceId.PIXIV ->
            "Pixiv"

        DiscoverySourceId.X ->
            "X"
    }
}

private fun sourceReferer(
    source: DiscoverySourceId
): String {
    return when (
        source
    ) {
        DiscoverySourceId.PIXIV ->
            "https://www.pixiv.net/"

        DiscoverySourceId.X ->
            "https://x.com/"
    }
}

private fun shouldLoadMore(
    gridState: LazyGridState,
    itemCount: Int
): Boolean {
    if (
        itemCount == 0
    ) {
        return false
    }

    val lastVisibleIndex =
        gridState
            .layoutInfo
            .visibleItemsInfo
            .lastOrNull()
            ?.index
            ?: return false

    return lastVisibleIndex >=
            itemCount - 4
}