package io.github.nelurea.muninn.ui.screen

import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.R
import io.github.nelurea.muninn.capture.usecase.RefreshCapturedWorkMetadataResult
import io.github.nelurea.muninn.capture.usecase.RefreshCapturedWorkMetadataUseCase
import io.github.nelurea.muninn.content.ContentRestriction
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.settings.ContentVisibility
import io.github.nelurea.muninn.settings.SensitiveContentVisibilityController
import io.github.nelurea.muninn.settings.visibilityFor
import io.github.nelurea.muninn.ui.capture.XMetadataRefreshSession
import io.github.nelurea.muninn.ui.media.LoopingVideoPlayer
import io.github.nelurea.muninn.ui.settings.SensitiveContentVisibilityControl
import kotlinx.coroutines.delay
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

private fun CapturedWorkWithMedia.visibilityFor(
    policy: io.github.nelurea.muninn.settings.SensitiveContentVisibilityPolicy
): ContentVisibility {
    val restriction =
        runCatching {
            ContentRestriction.valueOf(
                work.contentRestriction
            )
        }.getOrDefault(
            ContentRestriction.UNKNOWN
        )

    return restriction.visibilityFor(policy)
}

@Composable
fun GalleryScreen(
    repository: CapturedWorkRepository,
    sensitiveContentVisibilityController: SensitiveContentVisibilityController,
    contentRevision: Int,
    importInProgress: Boolean,
    onWorkClick: (Long, String?) -> Unit
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    val sensitivePolicy by
        sensitiveContentVisibilityController
            .policy
            .collectAsState()

    var works by remember {
        mutableStateOf(
            emptyList<CapturedWorkWithMedia>()
        )
    }

    var contextualizedWorkIds by remember {
        mutableStateOf(emptySet<Long>())
    }

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var searchResultIds by remember {
        mutableStateOf(emptySet<Long>())
    }

    var sourceFilter by rememberSaveable {
        mutableStateOf(GallerySourceFilter.ALL)
    }

    var mediaFilter by rememberSaveable {
        mutableStateOf(GalleryMediaFilter.ALL)
    }

    var highlightedOnly by rememberSaveable {
        mutableStateOf(false)
    }

    var contextualizedOnly by rememberSaveable {
        mutableStateOf(false)
    }

    var sortOrder by rememberSaveable {
        mutableStateOf(GallerySortOrder.NEWEST)
    }

    var showFilters by remember {
        mutableStateOf(false)
    }

    var selectedWorkIds by remember {
        mutableStateOf(emptySet<Long>())
    }

    val selectionMode = selectedWorkIds.isNotEmpty()

    var refreshQueueIds by remember {
        mutableStateOf(emptyList<Long>())
    }

    var refreshQueueIndex by remember {
        mutableStateOf(0)
    }

    var refreshingSelection by remember {
        mutableStateOf(false)
    }

    var refreshSuccessCount by remember {
        mutableStateOf(0)
    }

    var refreshFailureCount by remember {
        mutableStateOf(0)
    }

    var refreshSessionRetryCount by remember {
        mutableStateOf(0)
    }

    var refreshSkippedCount by remember {
        mutableStateOf(0)
    }

    var refreshMessage by remember {
        mutableStateOf<String?>(null)
    }

    val coroutineScope = rememberCoroutineScope()

    val refreshMetadataUseCase = remember(
        repository
    ) {
        RefreshCapturedWorkMetadataUseCase(
            repository
        )
    }

    val currentRefreshWork =
        refreshQueueIds
            .getOrNull(refreshQueueIndex)
            ?.let { workId ->
                works.firstOrNull {
                    it.work.id == workId
                }
            }

    fun finishRefreshItem(success: Boolean) {
        refreshSuccessCount += if (success) 1 else 0
        refreshFailureCount += if (success) 0 else 1
        val nextIndex = refreshQueueIndex + 1
        if (nextIndex < refreshQueueIds.size) {
            refreshSessionRetryCount = 0
            refreshQueueIndex = nextIndex
        } else {
            refreshingSelection = false
            refreshMessage = buildString {
                append(
                    "$refreshSuccessCount refreshed"
                )

                if (refreshFailureCount > 0) {
                    append(
                        " · $refreshFailureCount failed"
                    )
                }

                if (refreshSkippedCount > 0) {
                    append(
                        " · $refreshSkippedCount skipped"
                    )
                }
            }
            refreshQueueIds = emptyList()
            refreshQueueIndex = 0
        }
    }

    LaunchedEffect(contentRevision) {
        works = repository.getAllWithMedia()
        contextualizedWorkIds =
            repository.getContextualizedWorkIds()
    }

    LaunchedEffect(searchQuery) {
        val normalized = searchQuery.trim()

        if (normalized.isBlank()) {
            searchResultIds = emptySet()
            return@LaunchedEffect
        }

        delay(200)

        searchResultIds =
            repository.searchWorkIds(normalized)
    }

    val visibleWorks = remember(
        works, contextualizedWorkIds, searchQuery, searchResultIds,
        sourceFilter, mediaFilter, highlightedOnly, contextualizedOnly,
        sortOrder, sensitivePolicy
    ) {
        val filtered =
            works
                .asSequence()
                .filter {
                    it.visibilityFor(sensitivePolicy) !=
                        ContentVisibility.HIDDEN
                }
                .filter {
                    searchQuery.isBlank() ||
                        it.work.id in searchResultIds
                }
            .filter {
                when (sourceFilter) {
                    GallerySourceFilter.ALL -> true
                    GallerySourceFilter.PIXIV -> it.work.sourceType.equals("pixiv", true)
                    GallerySourceFilter.X -> it.work.sourceType.equals("x", true)
                }
            }
            .filter {
                when (mediaFilter) {
                    GalleryMediaFilter.ALL -> true
                    GalleryMediaFilter.IMAGE -> it.media.any { m -> m.mimeType.startsWith("image/", true) }
                    GalleryMediaFilter.VIDEO -> it.media.any { m -> m.mimeType.startsWith("video/", true) }
                }
            }
                .filter {
                    !highlightedOnly ||
                        it.media.any { media ->
                            media.isHighlighted
                        }
                }
                .filter {
                    !contextualizedOnly ||
                        it.work.id in contextualizedWorkIds
                }
                .toList()

        when (sortOrder) {
            GallerySortOrder.NEWEST ->
                filtered.sortedWith(
                    compareByDescending<CapturedWorkWithMedia> {
                        it.work.capturedAt
                    }.thenByDescending {
                        it.work.id
                    }
                )

            GallerySortOrder.OLDEST ->
                filtered.sortedWith(
                    compareBy<CapturedWorkWithMedia> {
                        it.work.capturedAt
                    }.thenBy {
                        it.work.id
                    }
                )
        }
    }

    LaunchedEffect(searchQuery, sourceFilter, mediaFilter, highlightedOnly, contextualizedOnly, sortOrder, sensitivePolicy) {
        if (visibleWorks.isNotEmpty()) gridState.scrollToItem(0)
        selectedWorkIds = selectedWorkIds.intersect(visibleWorks.map { it.work.id }.toSet())
    }

    val hasActiveFilters =
        searchQuery.isNotBlank() ||
            sourceFilter != GallerySourceFilter.ALL ||
            mediaFilter != GalleryMediaFilter.ALL ||
            highlightedOnly ||
            contextualizedOnly ||
            sortOrder != GallerySortOrder.NEWEST
    val activeFilterCount =
        listOf(
            searchQuery.isNotBlank(),
            sourceFilter != GallerySourceFilter.ALL,
            mediaFilter != GalleryMediaFilter.ALL,
            highlightedOnly,
            contextualizedOnly,
            sortOrder != GallerySortOrder.NEWEST
        ).count { it }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        selectionMode -> "${selectedWorkIds.size} selected${refreshMessage?.let { " · $it" } ?: ""}"
                        importInProgress -> "${visibleWorks.size} works · Restoring old images…"
                        activeFilterCount == 0 -> "${visibleWorks.size} works"
                        else -> "${visibleWorks.size} works · $activeFilterCount active"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                SensitiveContentVisibilityControl(
                    policy = sensitivePolicy,
                    onPolicyChange = sensitiveContentVisibilityController::setPolicy
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    IconButton(onClick = {
                        val allVisibleIds = visibleWorks.map { it.work.id }.toSet()
                        selectedWorkIds = if (selectedWorkIds == allVisibleIds) emptySet() else allVisibleIds
                    }) {
                        Icon(Icons.Default.SelectAll, "Select all Gallery works")
                    }
                    IconButton(enabled = !refreshingSelection, onClick = {
                        val selectedWorks = visibleWorks.filter { it.work.id in selectedWorkIds }
                        val refreshableWorks = selectedWorks.filter { it.work.sourceType.equals("x", true) && it.work.canonicalUrl.isNotBlank() }
                        refreshSuccessCount = 0
                        refreshFailureCount = 0
                        refreshSkippedCount = selectedWorks.size - refreshableWorks.size
                        refreshMessage = null
                        if (refreshableWorks.isEmpty()) {
                            refreshMessage = if (selectedWorks.isEmpty()) "Nothing selected" else "No refreshable X works"
                        } else {
                            refreshQueueIds = refreshableWorks.map { it.work.id }
                            refreshQueueIndex = 0
                            refreshSessionRetryCount = 0
                            selectedWorkIds = emptySet()
                            refreshingSelection = true
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh Gallery metadata")
                    }
                }
                IconButton(
                    onClick = {
                        if (selectionMode) {
                            selectedWorkIds = emptySet()
                        } else {
                            showFilters = true
                        }
                    }
                ) {
                    Icon(
                        if (selectionMode) Icons.Default.Close else Icons.Default.FilterList,
                        if (selectionMode) "Clear selection" else "Filter gallery",
                        tint = if (!selectionMode && hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (importInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
            if (refreshingSelection && refreshQueueIds.isNotEmpty()) {
                val completedCount = refreshQueueIndex.coerceAtMost(refreshQueueIds.size)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), Arrangement.SpaceBetween) {
                    Text("Refreshing metadata", style = MaterialTheme.typography.labelSmall)
                    Text("$completedCount / ${refreshQueueIds.size}", style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = {
                        if (refreshQueueIds.isEmpty()) 0f else completedCount.toFloat() / refreshQueueIds.size.toFloat()
                    }, Modifier.fillMaxWidth().height(2.dp)
                )
            } else if (!refreshMessage.isNullOrBlank()) {
                Text(refreshMessage.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp), state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleWorks, key = { it.work.id }) { item ->
                    val coverMedia = item.media.minByOrNull { it.mediaIndex }
                    val selected = item.work.id in selectedWorkIds
                    val visibility = item.visibilityFor(sensitivePolicy)
                    Column(
                        Modifier.fillMaxWidth().background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                        ).combinedClickable(
                            onClick = {
                                            if (selectionMode) {
                                    selectedWorkIds =
                                        if (selected) {
                                            selectedWorkIds - item.work.id
                                        } else {
                                            selectedWorkIds + item.work.id
                                        }
                                } else {
                                    onWorkClick(
                                        item.work.id,
                                        coverMedia
                                            ?.takeUnless {
                                                it.mimeType.startsWith(
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
                                    selectedWorkIds + item.work.id
                            }
                        )
                    ) {
                        coverMedia?.let { media ->
                            val mediaModifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.8f)
                                    .then(
                                        if (
                                            visibility ==
                                                ContentVisibility.BLURRED
                                        ) {
                                            Modifier.blur(24.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            if (
                                media.mimeType.startsWith(
                                    "video/",
                                    ignoreCase = true
                                )
                            ) {
                                LoopingVideoPlayer(
                                    uri = media.localUri,
                                    active = true,
                                    modifier = mediaModifier
                                )
                            } else {
                                AsyncImage(
                                    model =
                                        ImageRequest
                                            .Builder(context)
                                            .data(Uri.parse(media.localUri))
                                            .build(),
                                    contentDescription = null,
                                    modifier = mediaModifier,
                                    contentScale = ContentScale.Fit,
                                    onError = { state ->
                                        Log.e(
                                            "Muninn/Gallery",
                                            "Failed to load ${media.localUri}",
                                            state.result.throwable
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (refreshingSelection && currentRefreshWork != null) {
        key(currentRefreshWork.work.id, refreshSessionRetryCount) {
            XMetadataRefreshSession(
                canonicalUrl = currentRefreshWork.work.canonicalUrl,
                sourceId = currentRefreshWork.work.sourceId,
                onPayload = { payload ->
                    coroutineScope.launch {
                        when (refreshMetadataUseCase.refreshX(currentRefreshWork.work.id, payload)) {
                            is RefreshCapturedWorkMetadataResult.Success -> {
                                works = repository.getAllWithMedia()
                                finishRefreshItem(true)
                            }
                            is RefreshCapturedWorkMetadataResult.Failure -> finishRefreshItem(false)
                        }
                    }
                },
                onFailure = {
                    if (refreshSessionRetryCount < MAX_REFRESH_SESSION_RETRIES) refreshSessionRetryCount++ else finishRefreshItem(false)
                }
            )
        }
    }

    if (showFilters) {
        GalleryFilterSheet(
            searchQuery = searchQuery,
            sourceFilter = sourceFilter,
            mediaFilter = mediaFilter,
            highlightedOnly = highlightedOnly,
            contextualizedOnly = contextualizedOnly,
            sortOrder = sortOrder,
            hasActiveFilters = hasActiveFilters,
            onSearchQueryChange = {
                searchQuery = it
            },
            onSourceFilterChange = {
                sourceFilter = it
            },
            onMediaFilterChange = {
                mediaFilter = it
            },
            onHighlightedChange = {
                highlightedOnly = it
            },
            onContextualizedChange = {
                contextualizedOnly = it
            },
            onSortOrderChange = {
                sortOrder = it
            },
            onClear = {
                searchQuery = ""
                sourceFilter = GallerySourceFilter.ALL
                mediaFilter = GalleryMediaFilter.ALL
                highlightedOnly = false
                contextualizedOnly = false
                sortOrder = GallerySortOrder.NEWEST
            },
            onDismiss = {
                showFilters = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryFilterSheet(
    searchQuery: String,
    sourceFilter: GallerySourceFilter,
    mediaFilter: GalleryMediaFilter,
    highlightedOnly: Boolean,
    contextualizedOnly: Boolean,
    sortOrder: GallerySortOrder,
    hasActiveFilters: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSourceFilterChange: (GallerySourceFilter) -> Unit,
    onMediaFilterChange: (GalleryMediaFilter) -> Unit,
    onHighlightedChange: (Boolean) -> Unit,
    onContextualizedChange: (Boolean) -> Unit,
    onSortOrderChange: (GallerySortOrder) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp).verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Search & filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search title, author, tags, or context") }, singleLine = true
            )
            Spacer(
                modifier = Modifier.height(20.dp)
            )

            GalleryFilterSectionTitle(
                text = "Source"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GalleryFilterTile(R.drawable.ic_gallery_source_all, "All", sourceFilter == GallerySourceFilter.ALL) { onSourceFilterChange(GallerySourceFilter.ALL) }
                GalleryFilterTile(R.drawable.ic_gallery_source_pixiv, "Pixiv", sourceFilter == GallerySourceFilter.PIXIV) { onSourceFilterChange(GallerySourceFilter.PIXIV) }
                GalleryFilterTile(R.drawable.ic_gallery_source_x, "X", sourceFilter == GallerySourceFilter.X) { onSourceFilterChange(GallerySourceFilter.X) }
            }
            GallerySectionSpacer()

            GalleryFilterSectionTitle(
                text = "Media"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GalleryMaterialFilterTile(Icons.Default.Collections, "All", mediaFilter == GalleryMediaFilter.ALL) { onMediaFilterChange(GalleryMediaFilter.ALL) }
                GalleryFilterTile(R.drawable.ic_gallery_image, "Images", mediaFilter == GalleryMediaFilter.IMAGE) { onMediaFilterChange(GalleryMediaFilter.IMAGE) }
                GalleryMaterialFilterTile(Icons.Default.VideoLibrary, "Videos", mediaFilter == GalleryMediaFilter.VIDEO) { onMediaFilterChange(GalleryMediaFilter.VIDEO) }
            }
            GallerySectionSpacer()

            GalleryFilterSectionTitle(
                text = "Properties"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GalleryFilterTile(R.drawable.ic_gallery_highlight, "Highlighted", highlightedOnly) { onHighlightedChange(!highlightedOnly) }
                GalleryMaterialFilterTile(Icons.Default.StickyNote2, "Notes", contextualizedOnly) { onContextualizedChange(!contextualizedOnly) }
            }
            GallerySectionSpacer()

            GalleryFilterSectionTitle(
                text = "Saved"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GalleryFilterTile(R.drawable.ic_gallery_newest, "Newest", sortOrder == GallerySortOrder.NEWEST) { onSortOrderChange(GallerySortOrder.NEWEST) }
                GalleryFilterTile(R.drawable.ic_gallery_oldest, "Oldest", sortOrder == GallerySortOrder.OLDEST) { onSortOrderChange(GallerySortOrder.OLDEST) }
            }
            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Button(
                enabled = hasActiveFilters,
                onClick = onClear,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Clear all"
                )
            }
        }
    }
}

@Composable
private fun GalleryFilterSectionTitle(
    text: String
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 9.dp)
    )
}

@Composable
private fun GallerySectionSpacer() {
    Spacer(
        modifier = Modifier.height(20.dp)
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
        label = label,
        selected = selected,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun GalleryFilterTile(
    @DrawableRes iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    GalleryFilterTileContainer(
        label = label,
        selected = selected,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(30.dp)
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            contentColor =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(68.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

private const val MAX_REFRESH_SESSION_RETRIES = 1
