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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.discovery.ArtworkPreviewUiState
import io.github.nelurea.muninn.discovery.DiscoveryViewModel
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.core.text.HtmlCompat
import androidx.compose.ui.text.style.TextOverflow

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
        DiscoveryContent(
            viewModel =
                viewModel,
            gridState =
                gridState,
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
                            ?: return@ArtworkPreviewOverlay

                    onItemClick(
                        item
                    )
                },
                onTagClick = {
                        tag ->

                    viewModel
                        .searchByTag(
                            tag
                        )
                }
            )
        }
    }
}

@Composable
private fun DiscoveryContent(
    viewModel: DiscoveryViewModel,
    gridState: LazyGridState,
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
            DiscoveryModeButton(
                label =
                    "Latest",
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
                    "Bookmarks",
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
                            "Search Pixiv"
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
                            }
                        )
                )

                Button(
                    onClick = {
                        viewModel
                            .submitSearch()
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
                        ) { item ->

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
    onTagClick: (String) -> Unit
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
                            1
                    ) {
                            page ->

                        val media =
                            preview.media[
                                page
                            ]

                        ArtworkPagerPage(
                            media =
                                media,
                            selected =
                                media.mediaIndex in
                                        state.selectedMediaIndices,
                            controlsVisible =
                                controlsVisible,
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
                                    onClose
                            ) {
                                Text(
                                    "Close"
                                )
                            }

                            OutlinedButton(
                                onClick =
                                    onOpenOriginal
                            ) {
                                Text(
                                    "Pixiv"
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
                            onTagClick =
                                onTagClick,
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
    preview: io.github.nelurea.muninn.discovery.model.ArtworkPreview,
    selected: Boolean,
    selectedCount: Int,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var captionExpanded by remember(
        preview.sourceItemId
    ) {
        mutableStateOf(
            false
        )
    }
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
                                    "https://www.pixiv.net/"
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
                        plainCaption.isNotBlank()
                    ) {
                        var captionExpanded by remember(
                            preview.sourceItemId
                        ) {
                            mutableStateOf(
                                false
                            )
                        }

                        var captionOverflowed by remember(
                            preview.sourceItemId
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
                                                captionExpanded ||
                                                        captionOverflowed
                                        ) {
                                            captionExpanded =
                                                !captionExpanded
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
                                        captionExpanded
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
                                        !captionExpanded
                                    ) {
                                        captionOverflowed =
                                            result.hasVisualOverflow
                                    }
                                }
                            )

                            if (
                                captionExpanded ||
                                captionOverflowed
                            ) {
                                Text(
                                    text =
                                        if (
                                            captionExpanded
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
                                                captionExpanded =
                                                    !captionExpanded
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
                                Modifier.clickable {
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
        }
    }
}

@Composable
private fun ArtworkPagerPage(
    media: ArtworkPreviewMedia,
    selected: Boolean,
    controlsVisible: Boolean,
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
                "https://www.pixiv.net/"
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
                    media.mediaIndex
                ) {
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
                    media.mediaIndex
                ) {
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
private fun DiscoveryModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (
        selected
    ) {
        Button(
            onClick = {
            }
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
                "https://www.pixiv.net/"
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