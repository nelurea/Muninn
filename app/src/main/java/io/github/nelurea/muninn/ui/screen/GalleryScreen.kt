package io.github.nelurea.muninn.ui.screen

import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.R
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.capture.usecase.RefreshCapturedWorkMetadataResult
import io.github.nelurea.muninn.capture.usecase.RefreshCapturedWorkMetadataUseCase
import io.github.nelurea.muninn.ui.capture.XMetadataRefreshSession
import io.github.nelurea.muninn.ui.media.LoopingVideoPlayer
import kotlinx.coroutines.launch

private enum class GallerySourceFilter {
    ALL,
    PIXIV,
    X
}

private enum class GalleryMediaFilter {
    ALL,
    IMAGE,
    VIDEO
}

private enum class GallerySortOrder {
    NEWEST,
    OLDEST
}

@Composable
fun GalleryScreen(
    repository: CapturedWorkRepository,
    onWorkClick: (Long, String?) -> Unit
) {
    val context =
        LocalContext.current

    val listState =
        rememberLazyListState()

    var works by remember {
        mutableStateOf(
            emptyList<CapturedWorkWithMedia>()
        )
    }

    var contextualizedWorkIds by remember {
        mutableStateOf(
            emptySet<Long>()
        )
    }

    var sourceFilter by remember {
        mutableStateOf(
            GallerySourceFilter.ALL
        )
    }

    var mediaFilter by remember {
        mutableStateOf(
            GalleryMediaFilter.ALL
        )
    }

    var highlightedOnly by remember {
        mutableStateOf(
            false
        )
    }

    var contextualizedOnly by remember {
        mutableStateOf(
            false
        )
    }

    var sortOrder by remember {
        mutableStateOf(
            GallerySortOrder.NEWEST
        )
    }

    var showFilters by remember {
        mutableStateOf(
            false
        )
    }

    var selectedWorkIds by remember {
        mutableStateOf(
            emptySet<Long>()
        )
    }

    val selectionMode =
        selectedWorkIds.isNotEmpty()

    var refreshQueueIds by remember {
        mutableStateOf(
            emptyList<Long>()
        )
    }

    var refreshQueueIndex by remember {
        mutableStateOf(
            0
        )
    }

    var refreshingSelection by remember {
        mutableStateOf(
            false
        )
    }

    var refreshSuccessCount by remember {
        mutableStateOf(
            0
        )
    }

    var refreshFailureCount by remember {
        mutableStateOf(
            0
        )
    }

    var refreshSessionRetryCount by remember {
        mutableStateOf(
            0
        )
    }

    var refreshSkippedCount by remember {
        mutableStateOf(
            0
        )
    }

    var refreshMessage by remember {
        mutableStateOf<String?>(
            null
        )
    }

    val coroutineScope =
        rememberCoroutineScope()

    val refreshMetadataUseCase =
        remember(
            repository
        ) {
            RefreshCapturedWorkMetadataUseCase(
                repository
            )
        }

    val currentRefreshWork =
        refreshQueueIds
            .getOrNull(
                refreshQueueIndex
            )
            ?.let { workId ->
                works.firstOrNull {
                    it.work.id ==
                        workId
                }
            }

    val finishRefreshItem:
        (Boolean) -> Unit = {
            success ->

            val nextSuccessCount =
                refreshSuccessCount +
                    if (
                        success
                    ) {
                        1
                    } else {
                        0
                    }

            val nextFailureCount =
                refreshFailureCount +
                    if (
                        success
                    ) {
                        0
                    } else {
                        1
                    }

            refreshSuccessCount =
                nextSuccessCount

            refreshFailureCount =
                nextFailureCount

            val nextIndex =
                refreshQueueIndex + 1

            if (
                nextIndex <
                refreshQueueIds.size
            ) {
                refreshSessionRetryCount =
                    0

                refreshQueueIndex =
                    nextIndex
            } else {
                refreshingSelection =
                    false

                refreshMessage =
                    buildString {
                        append(
                            "$nextSuccessCount refreshed"
                        )

                        if (
                            nextFailureCount > 0
                        ) {
                            append(
                                " · $nextFailureCount failed"
                            )
                        }

                        if (
                            refreshSkippedCount > 0
                        ) {
                            append(
                                " · $refreshSkippedCount skipped"
                            )
                        }
                    }

                refreshQueueIds =
                    emptyList()

                refreshQueueIndex =
                    0
            }
        }

    LaunchedEffect(
        Unit
    ) {
        works =
            repository
                .getAllWithMedia()

        contextualizedWorkIds =
            repository
                .getContextualizedWorkIds()
    }

    val visibleWorks =
        remember(
            works,
            contextualizedWorkIds,
            sourceFilter,
            mediaFilter,
            highlightedOnly,
            contextualizedOnly,
            sortOrder
        ) {
            val filtered =
                works
                    .asSequence()
                    .filter { item ->
                        when (
                            sourceFilter
                        ) {
                            GallerySourceFilter.ALL ->
                                true

                            GallerySourceFilter.PIXIV ->
                                item.work.sourceType
                                    .equals(
                                        "pixiv",
                                        ignoreCase = true
                                    )

                            GallerySourceFilter.X ->
                                item.work.sourceType
                                    .equals(
                                        "x",
                                        ignoreCase = true
                                    )
                        }
                    }
                    .filter { item ->
                        when (
                            mediaFilter
                        ) {
                            GalleryMediaFilter.ALL ->
                                true

                            GalleryMediaFilter.IMAGE ->
                                item.media.any { media ->
                                    media.mimeType
                                        .startsWith(
                                            "image/",
                                            ignoreCase = true
                                        )
                                }

                            GalleryMediaFilter.VIDEO ->
                                item.media.any { media ->
                                    media.mimeType
                                        .startsWith(
                                            "video/",
                                            ignoreCase = true
                                        )
                                }
                        }
                    }
                    .filter { item ->
                        !highlightedOnly ||
                            item.media.any {
                                it.isHighlighted
                            }
                    }
                    .filter { item ->
                        !contextualizedOnly ||
                            item.work.id in
                                contextualizedWorkIds
                    }
                    .toList()

            when (
                sortOrder
            ) {
                GallerySortOrder.NEWEST ->
                    filtered.sortedWith(
                        compareByDescending<
                            CapturedWorkWithMedia
                        > {
                            it.work.capturedAt
                        }.thenByDescending {
                            it.work.id
                        }
                    )

                GallerySortOrder.OLDEST ->
                    filtered.sortedWith(
                        compareBy<
                            CapturedWorkWithMedia
                        > {
                            it.work.capturedAt
                        }.thenBy {
                            it.work.id
                        }
                    )
            }
        }

    LaunchedEffect(
        sourceFilter,
        mediaFilter,
        highlightedOnly,
        contextualizedOnly,
        sortOrder
    ) {
        if (
            visibleWorks.isNotEmpty()
        ) {
            listState.scrollToItem(
                0
            )
        }
    }

    val hasActiveFilters =
        sourceFilter !=
            GallerySourceFilter.ALL ||
        mediaFilter !=
            GalleryMediaFilter.ALL ||
        highlightedOnly ||
        contextualizedOnly ||
        sortOrder !=
            GallerySortOrder.NEWEST

    val activeFilterCount =
        listOf(
            sourceFilter !=
                GallerySourceFilter.ALL,
            mediaFilter !=
                GalleryMediaFilter.ALL,
            highlightedOnly,
            contextualizedOnly,
            sortOrder !=
                GallerySortOrder.NEWEST
        ).count {
            it
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
                        start = 16.dp,
                        end = 8.dp,
                        top = 2.dp,
                        bottom = 2.dp
                    ),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text =
                    if (
                        selectionMode
                    ) {
                        "${selectedWorkIds.size} selected" + (refreshMessage?.let { " · $it" } ?: "")
                    } else if (
                        activeFilterCount == 0
                    ) {
                        "${visibleWorks.size} works"
                    } else {
                        "${visibleWorks.size} works · $activeFilterCount active"
                    },
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )

            if (
                selectionMode
            ) {
                IconButton(
                    enabled =
                        !refreshingSelection,
                    onClick = {
                        val selectedWorks =
                            works

                        val refreshableWorks =
                            selectedWorks.filter {
                                it.work.sourceType
                                    .equals(
                                        "x",
                                        ignoreCase = true
                                    ) &&
                                    it.work.canonicalUrl
                                        .isNotBlank()
                            }

                        refreshSuccessCount =
                            0

                        refreshFailureCount =
                            0

                        refreshSkippedCount =
                            selectedWorks.size -
                                refreshableWorks.size

                        refreshMessage =
                            null

                        if (
                            refreshableWorks.isEmpty()
                        ) {
                            refreshMessage =
                                if (
                                    selectedWorks.isEmpty()
                                ) {
                                    "Nothing selected"
                                } else {
                                    "No refreshable X works"
                                }
                        } else {
                            refreshQueueIds =
                                refreshableWorks.map {
                                    it.work.id
                                }

                            refreshQueueIndex =
                                0

                            refreshSessionRetryCount =
                                0

                            refreshingSelection =
                                true
                        }
                    }
                ) {
                    if (
                        refreshingSelection
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(
                                    22.dp
                                ),
                            strokeWidth =
                                2.dp
                        )
                    } else {
                        Icon(
                            imageVector =
                                Icons.Default.Refresh,
                            contentDescription =
                                "Refresh selected metadata"
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    if (
                        selectionMode
                    ) {
                        selectedWorkIds =
                            emptySet()
                    } else {
                        showFilters =
                            true
                    }
                }
            ) {
                Icon(
                    imageVector =
                        if (
                            selectionMode
                        ) {
                            Icons.Default.Close
                        } else {
                            Icons.Default.FilterList
                        },
                    contentDescription =
                        if (
                            selectionMode
                        ) {
                            "Clear selection"
                        } else {
                            "Filter gallery"
                        },
                    tint =
                        if (
                            !selectionMode &&
                            hasActiveFilters
                        ) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onSurface
                        }
                )
            }
        }

        LazyColumn(
            state =
                listState,
            modifier =
                Modifier.fillMaxSize()
        ) {
            items(
                items =
                    visibleWorks,
                key = {
                    it.work.id
                }
            ) { item ->

                val coverMedia =
                    item.media
                        .minByOrNull {
                            it.mediaIndex
                        }

                val selected =
                    item.work.id in
                        selectedWorkIds

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (
                                    selected
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .primaryContainer
                                        .copy(
                                            alpha = 0.35f
                                        )
                                } else {
                                    Color.Transparent
                                }
                            )
                            .combinedClickable(
                                onClick = {
                                    if (
                                        selectionMode
                                    ) {
                                        selectedWorkIds =
                                            if (
                                                selected
                                            ) {
                                                selectedWorkIds -
                                                    item.work.id
                                            } else {
                                                selectedWorkIds +
                                                    item.work.id
                                            }
                                    } else {
                                        onWorkClick(
                                            item.work.id,
                                            coverMedia
                                                ?.takeUnless {
                                                    it.mimeType
                                                        .startsWith(
                                                            "video/",
                                                            ignoreCase = true
                                                        )
                                                }
                                                ?.localUri
                                        )
                                    }
                                },
                                onLongClick = {
                                    selectedWorkIds =
                                        selectedWorkIds +
                                            item.work.id
                                }
                            )
                            .padding(
                                bottom = 20.dp
                            )
                ) {
                    coverMedia
                        ?.let { media ->

                            val localUri =
                                Uri.parse(
                                    media.localUri
                                )

                            if (
                                media.mimeType
                                    .startsWith(
                                        "video/",
                                        ignoreCase = true
                                    )
                            ) {
                                LoopingVideoPlayer(
                                    uri =
                                        media.localUri,
                                    active =
                                        true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(
                                                280.dp
                                            )
                                )
                            } else {
                                AsyncImage(
                                    model =
                                        ImageRequest
                                            .Builder(
                                                context
                                            )
                                            .data(
                                                localUri
                                            )
                                            .build(),
                                    contentDescription =
                                        null,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(
                                                280.dp
                                            ),
                                    contentScale =
                                        ContentScale.Fit,
                                    onError = { state ->
                                        Log.e(
                                            "Muninn/Gallery",
                                            "Failed to load ${media.localUri}",
                                            state
                                                .result
                                                .throwable
                                        )
                                    }
                                )
                            }
                        }

                    Column(
                        modifier =
                            Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 8.dp
                            )
                    ) {
                        item.work.title
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let { title ->
                                Text(
                                    text =
                                        title,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium
                                )
                            }

                        Text(
                            text =
                                item.work.authorName,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (
        refreshingSelection &&
        currentRefreshWork != null
    ) {
        key(
            currentRefreshWork.work.id,
            refreshSessionRetryCount
        ) {
            XMetadataRefreshSession(
                canonicalUrl =
                currentRefreshWork
                    .work
                    .canonicalUrl,
            sourceId =
                currentRefreshWork
                    .work
                    .sourceId,
            onPayload = {
                payload ->

                coroutineScope.launch {
                    val result =
                        refreshMetadataUseCase
                            .refreshX(
                                workId =
                                    currentRefreshWork
                                        .work
                                        .id,
                                payload =
                                    payload
                            )

                    when (
                        result
                    ) {
                        is RefreshCapturedWorkMetadataResult.Success -> {
                            Log.d(
                                "Muninn/GalleryRefresh",
                                "success workId=${currentRefreshWork.work.id} sourceId=${currentRefreshWork.work.sourceId}"
                            )

                            works =
                                repository
                                    .getAllWithMedia()

                            finishRefreshItem(
                                true
                            )
                        }

                        is RefreshCapturedWorkMetadataResult.Failure -> {
                            Log.e(
                                "Muninn/GalleryRefresh",
                                "refreshX failed workId=${currentRefreshWork.work.id} sourceId=${currentRefreshWork.work.sourceId} url=${currentRefreshWork.work.canonicalUrl} result=$result"
                            )

                            finishRefreshItem(
                                false
                            )
                        }
                    }
                }
            },
                onFailure = {
                    if (
                        refreshSessionRetryCount <
                        MAX_REFRESH_SESSION_RETRIES
                    ) {
                        Log.w(
                            "Muninn/GalleryRefresh",
                            "session retry workId=${currentRefreshWork.work.id} sourceId=${currentRefreshWork.work.sourceId} retry=${refreshSessionRetryCount + 1}"
                        )

                        refreshSessionRetryCount =
                            refreshSessionRetryCount + 1
                    } else {
                        Log.e(
                            "Muninn/GalleryRefresh",
                            "session failed after retry workId=${currentRefreshWork.work.id} sourceId=${currentRefreshWork.work.sourceId} url=${currentRefreshWork.work.canonicalUrl}"
                        )

                        finishRefreshItem(
                            false
                        )
                    }
                }
            )
        }
    }

    if (
        showFilters
    ) {
        GalleryFilterSheet(
            sourceFilter =
                sourceFilter,
            mediaFilter =
                mediaFilter,
            highlightedOnly =
                highlightedOnly,
            contextualizedOnly =
                contextualizedOnly,
            sortOrder =
                sortOrder,
            hasActiveFilters =
                hasActiveFilters,
            onSourceFilterChange = {
                sourceFilter =
                    it
            },
            onMediaFilterChange = {
                mediaFilter =
                    it
            },
            onHighlightedChange = {
                highlightedOnly =
                    it
            },
            onContextualizedChange = {
                contextualizedOnly =
                    it
            },
            onSortOrderChange = {
                sortOrder =
                    it
            },
            onClear = {
                sourceFilter =
                    GallerySourceFilter.ALL

                mediaFilter =
                    GalleryMediaFilter.ALL

                highlightedOnly =
                    false

                contextualizedOnly =
                    false

                sortOrder =
                    GallerySortOrder.NEWEST
            },
            onDismiss = {
                showFilters =
                    false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryFilterSheet(
    sourceFilter: GallerySourceFilter,
    mediaFilter: GalleryMediaFilter,
    highlightedOnly: Boolean,
    contextualizedOnly: Boolean,
    sortOrder: GallerySortOrder,
    hasActiveFilters: Boolean,
    onSourceFilterChange: (GallerySourceFilter) -> Unit,
    onMediaFilterChange: (GalleryMediaFilter) -> Unit,
    onHighlightedChange: (Boolean) -> Unit,
    onContextualizedChange: (Boolean) -> Unit,
    onSortOrderChange: (GallerySortOrder) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest =
            onDismiss
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 28.dp
                    )
        ) {
            Text(
                text =
                    "Filter & sort",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        20.dp
                    )
            )

            GalleryFilterSectionTitle(
                "Source"
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_source_all,
                    label =
                        "All",
                    selected =
                        sourceFilter ==
                            GallerySourceFilter.ALL,
                    onClick = {
                        onSourceFilterChange(
                            GallerySourceFilter.ALL
                        )
                    }
                )

                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_source_pixiv,
                    label =
                        "Pixiv",
                    selected =
                        sourceFilter ==
                            GallerySourceFilter.PIXIV,
                    onClick = {
                        onSourceFilterChange(
                            GallerySourceFilter.PIXIV
                        )
                    }
                )

                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_source_x,
                    label =
                        "X",
                    selected =
                        sourceFilter ==
                            GallerySourceFilter.X,
                    onClick = {
                        onSourceFilterChange(
                            GallerySourceFilter.X
                        )
                    }
                )
            }

            GallerySectionSpacer()

            GalleryFilterSectionTitle(
                "Media"
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                GalleryMaterialFilterTile(
                    icon =
                        Icons.Default.Collections,
                    label =
                        "All",
                    selected =
                        mediaFilter ==
                            GalleryMediaFilter.ALL,
                    onClick = {
                        onMediaFilterChange(
                            GalleryMediaFilter.ALL
                        )
                    }
                )

                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_image,
                    label =
                        "Images",
                    selected =
                        mediaFilter ==
                            GalleryMediaFilter.IMAGE,
                    onClick = {
                        onMediaFilterChange(
                            GalleryMediaFilter.IMAGE
                        )
                    }
                )

                GalleryMaterialFilterTile(
                    icon =
                        Icons.Default.VideoLibrary,
                    label =
                        "Videos",
                    selected =
                        mediaFilter ==
                            GalleryMediaFilter.VIDEO,
                    onClick = {
                        onMediaFilterChange(
                            GalleryMediaFilter.VIDEO
                        )
                    }
                )
            }

            GallerySectionSpacer()

            GalleryFilterSectionTitle(
                "Properties"
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_highlight,
                    label =
                        "Highlighted",
                    selected =
                        highlightedOnly,
                    onClick = {
                        onHighlightedChange(
                            !highlightedOnly
                        )
                    }
                )

                GalleryMaterialFilterTile(
                    icon =
                        Icons.Default.StickyNote2,
                    label =
                        "Notes",
                    selected =
                        contextualizedOnly,
                    onClick = {
                        onContextualizedChange(
                            !contextualizedOnly
                        )
                    }
                )
            }

            GallerySectionSpacer()

            GalleryFilterSectionTitle(
                "Saved"
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_newest,
                    label =
                        "Newest",
                    selected =
                        sortOrder ==
                            GallerySortOrder.NEWEST,
                    onClick = {
                        onSortOrderChange(
                            GallerySortOrder.NEWEST
                        )
                    }
                )

                GalleryFilterTile(
                    iconRes =
                        R.drawable
                            .ic_gallery_oldest,
                    label =
                        "Oldest",
                    selected =
                        sortOrder ==
                            GallerySortOrder.OLDEST,
                    onClick = {
                        onSortOrderChange(
                            GallerySortOrder.OLDEST
                        )
                    }
                )
            }

            if (
                hasActiveFilters
            ) {
                Spacer(
                    modifier =
                        Modifier.height(
                            24.dp
                        )
                )

                Button(
                    onClick =
                        onClear,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Clear filters"
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryFilterSectionTitle(
    text: String
) {
    Text(
        text =
            text,
        style =
            MaterialTheme
                .typography
                .labelLarge,
        color =
            MaterialTheme
                .colorScheme
                .onSurfaceVariant,
        modifier =
            Modifier.padding(
                bottom = 9.dp
            )
    )
}

@Composable
private fun GallerySectionSpacer() {
    Spacer(
        modifier =
            Modifier.height(
                20.dp
            )
    )
}

@Composable
private fun GalleryMaterialFilterTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GalleryFilterTileContainer(
        label =
            label,
        selected =
            selected,
        onClick =
            onClick
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                label,
            modifier =
                Modifier.size(
                    30.dp
                )
        )
    }
}
@Composable
private fun GalleryFilterTile(
    @DrawableRes
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GalleryFilterTileContainer(
        label =
            label,
        selected =
            selected,
        onClick =
            onClick
    ) {
        Icon(
            painter =
                painterResource(
                    iconRes
                ),
            contentDescription =
                label,
            modifier =
                Modifier.size(
                    30.dp
                )
        )
    }
}

@Composable
private fun GalleryFilterTileContainer(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier =
            Modifier.width(
                76.dp
            )
    ) {
        Surface(
            onClick =
                onClick,
            shape =
                RoundedCornerShape(
                    18.dp
                ),
            color =
                if (
                    selected
                ) {
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
                } else {
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                },
            contentColor =
                if (
                    selected
                ) {
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer
                } else {
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                },
            modifier =
                Modifier.size(
                    68.dp
                )
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                content()
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )

        Text(
            text =
                label,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            maxLines =
                1
        )
    }
}

private const val MAX_REFRESH_SESSION_RETRIES =
    1
