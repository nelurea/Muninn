package io.github.nelurea.muninn.ui.capture

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.nelurea.muninn.capture.usecase.RefreshCapturedWorkMetadataResult
import io.github.nelurea.muninn.capture.usecase.RefreshCapturedWorkMetadataUseCase
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.ui.media.LoopingVideoPlayer
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CapturedWorkDetailScreen(
    workId: Long,
    initialPreviewUri: String? = null,
    repository: CapturedWorkRepository
) {
    var initialPreviewSucceeded by remember(
        workId,
        initialPreviewUri
    ) {
        mutableStateOf(
            false
        )
    }

    var initialPreviewFailed by remember(
        workId,
        initialPreviewUri
    ) {
        mutableStateOf(
            false
        )
    }

    var capturedWork by remember {
        mutableStateOf<CapturedWorkWithMedia?>(
            null
        )
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var showContextEditor by remember {
        mutableStateOf(false)
    }

    var controlsVisible by remember(
        workId
    ) {
        mutableStateOf(
            false
        )
    }

    var currentPageZoomed by remember(
        workId
    ) {
        mutableStateOf(
            false
        )
    }


    var refreshingMetadata by remember(
        workId
    ) {
        mutableStateOf(
            false
        )
    }

    var refreshMessage by remember(
        workId
    ) {
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
    LaunchedEffect(
        workId
    ) {
        capturedWork =
            repository.getWithMediaById(
                workId
            )

        loading =
            false
    }

    Scaffold(
        floatingActionButton = {
            if (
                controlsVisible &&
                !loading &&
                capturedWork != null
            ) {
                FloatingActionButton(
                    onClick = {
                        showContextEditor =
                            true
                    }
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Edit,
                        contentDescription =
                            "Edit context"
                    )
                }
            }
        }
    ) { paddingValues ->

        val canonicalInitialPreviewMatches =
            initialPreviewUri != null &&
                capturedWork
                    ?.media
                    ?.minByOrNull {
                        it.mediaIndex
                    }
                    ?.localUri == initialPreviewUri

        val showInitialPreview =
            initialPreviewUri != null &&
                !initialPreviewFailed &&
                (
                    loading ||
                        (
                            !initialPreviewSucceeded &&
                                canonicalInitialPreviewMatches
                        )
                    )

        when {
            showInitialPreview -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    AsyncImage(
                        model =
                            initialPreviewUri,
                        contentDescription =
                            null,
                        onSuccess = {
                            initialPreviewSucceeded =
                                true
                        },
                        onError = {
                            initialPreviewFailed =
                                true
                        },
                        modifier =
                            Modifier
                                .fillMaxSize(),
                        contentScale =
                            ContentScale.Fit
                    )
                }
            }

            loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            capturedWork == null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        "Captured work not found"
                    )
                }
            }

            else -> {
                val item =
                    capturedWork
                        ?: return@Scaffold

                val media =
                    remember(
                        item.media
                    ) {
                        item.media
                            .sortedBy {
                                it.mediaIndex
                            }
                    }

                val pagerState =
                    rememberPagerState(
                        pageCount = {
                            media.size
                        }
                    )

                val currentMedia =
                    media.getOrNull(
                        pagerState.currentPage
                    )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            )
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                    ) {
                        if (
                            media.isNotEmpty()
                        ) {
                            HorizontalPager(
                                state =
                                    pagerState,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(
                                            1f
                                        ),
                                userScrollEnabled =
                                    !currentPageZoomed
                            ) { page ->

                                val pageMedia =
                                    media[page]

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                ) {
                                    ZoomableCapturedMediaPage(
                                        localUri =
                                            pageMedia.localUri,
                                        mimeType =
                                            pageMedia.mimeType,
                                        mediaId =
                                            pageMedia.id,
                                        active =
                                            page ==
                                                pagerState.currentPage,
                                        onZoomedChange = {
                                                zoomed ->

                                            if (
                                                page ==
                                                pagerState.currentPage
                                            ) {
                                                currentPageZoomed =
                                                    zoomed
                                            }
                                        },
                                        onToggleControls = {
                                            controlsVisible =
                                                !controlsVisible
                                        }
                                    )

                                    if (
                                        controlsVisible &&
                                        pageMedia
                                            .isHighlighted
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .padding(
                                                        12.dp
                                                    )
                                                    .size(
                                                        28.dp
                                                    )
                                                    .background(
                                                        color =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .surface
                                                                .copy(
                                                                    alpha =
                                                                        0.8f
                                                                ),
                                                        shape =
                                                            CircleShape
                                                    )
                                                    .align(
                                                        Alignment.TopEnd
                                                    ),
                                            contentAlignment =
                                                Alignment.Center
                                        ) {
                                            Text(
                                                text =
                                                    "✓",
                                                fontWeight =
                                                    FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }


                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize(),
                                contentAlignment =
                                    Alignment.Center
                            ) {
                                Text(
                                    "No saved media"
                                )
                            }
                        }
                    }

                    if (
                        controlsVisible
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    if (
                                        refreshingMetadata
                                    ) {
                                        "Refreshing metadata..."
                                    } else {
                                        refreshMessage
                                            ?: "Captured Work"
                                    }
                                )
                            },
                            actions = {
                                if (
                                    item.work.sourceType ==
                                    "x"
                                ) {
                                    if (
                                        refreshingMetadata
                                    ) {
                                        CircularProgressIndicator(
                                            modifier =
                                                Modifier.size(
                                                    24.dp
                                                ),
                                            strokeWidth =
                                                2.dp
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                refreshMessage =
                                                    null

                                                refreshingMetadata =
                                                    true
                                            }
                                        ) {
                                            Icon(
                                                imageVector =
                                                    Icons.Default.Refresh,
                                                contentDescription =
                                                    "Refresh metadata"
                                            )
                                        }
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.TopCenter
                                    )
                                    .fillMaxWidth()
                        )
                    }

                    if (
                        controlsVisible &&
                        media.size > 1
                    ) {
                        Text(
                            text =
                                "${pagerState.currentPage + 1} / ${media.size}",
                            modifier =
                                Modifier
                                    .align(
                                        Alignment.BottomCenter
                                    )
                                    .padding(
                                        bottom = 12.dp
                                    )
                                    .background(
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .surface
                                                .copy(
                                                    alpha = 0.8f
                                                ),
                                        shape =
                                            CircleShape
                                    )
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 6.dp
                                    ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }

                    if (
                        refreshingMetadata &&
                        item.work.sourceType ==
                        "x"
                    ) {
                        XMetadataRefreshSession(
                            canonicalUrl =
                                item.work.canonicalUrl,
                            sourceId =
                                item.work.sourceId,
                            onPayload = {
                                    payload ->

                                coroutineScope.launch {
                                    when (
                                        val result =
                                            refreshMetadataUseCase
                                                .refreshX(
                                                    workId =
                                                        workId,
                                                    payload =
                                                        payload
                                                )
                                    ) {
                                        is RefreshCapturedWorkMetadataResult.Success -> {
                                            capturedWork =
                                                repository.getWithMediaById(
                                                    workId
                                                )

                                            refreshMessage =
                                                if (
                                                    result.addedTagCount >
                                                    0
                                                ) {
                                                    "Metadata updated · +${result.addedTagCount} tags"
                                                } else {
                                                    "Metadata is up to date"
                                                }

                                            refreshingMetadata =
                                                false
                                        }

                                        is RefreshCapturedWorkMetadataResult.Failure -> {
                                            refreshMessage =
                                                result.error

                                            refreshingMetadata =
                                                false
                                        }
                                    }
                                }
                            },
                            onFailure = {
                                    error ->

                                refreshMessage =
                                    error

                                refreshingMetadata =
                                    false
                            }
                        )
                    }

                    if (
                        showContextEditor
                    ) {
                        ContextEditorSheet(
                            workId =
                                workId,
                            currentMediaId =
                                currentMedia?.id,
                            capturedWork =
                                item,
                            repository =
                                repository,
                            onDismiss = {
                                showContextEditor =
                                    false
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ZoomableCapturedMediaPage(
    localUri: String,
    mimeType: String,
    mediaId: Long,
    active: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onToggleControls: () -> Unit
) {
    var scale by remember(
        mediaId
    ) {
        mutableFloatStateOf(
            1f
        )
    }

    var offsetX by remember(
        mediaId
    ) {
        mutableFloatStateOf(
            0f
        )
    }

    var offsetY by remember(
        mediaId
    ) {
        mutableFloatStateOf(
            0f
        )
    }

    var viewportSize by remember(
        mediaId
    ) {
        mutableStateOf(
            IntSize.Zero
        )
    }

    val zoomed =
        scale > 1.01f

    val snapToOneThreshold =
        1.08f

    LaunchedEffect(
        active
    ) {
        if (
            !active
        ) {
            scale =
                1f

            offsetX =
                0f

            offsetY =
                0f
        }
    }

    LaunchedEffect(
        active,
        zoomed
    ) {
        if (
            active
        ) {
            onZoomedChange(
                zoomed
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                        size ->

                    viewportSize =
                        size
                }
                .pointerInput(
                    mediaId,
                    active
                ) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed =
                                false
                        )

                        var transformGesture =
                            scale > 1.01f

                        do {
                            val event =
                                awaitPointerEvent()

                            val pressed =
                                event.changes
                                    .filter {
                                        it.pressed
                                    }

                            val pointerCount =
                                pressed.size

                            if (
                                !transformGesture &&
                                pointerCount >= 2
                            ) {
                                transformGesture =
                                    true
                            }

                            if (
                                transformGesture
                            ) {
                                val zoomChange =
                                    if (
                                        pointerCount >= 2
                                    ) {
                                        event.calculateZoom()
                                    } else {
                                        1f
                                    }

                                val panChange =
                                    event.calculatePan()

                                val newScale =
                                    (
                                        scale *
                                            zoomChange
                                        ).coerceIn(
                                        1f,
                                        5f
                                    )

                                if (
                                    newScale <=
                                    1.01f
                                ) {
                                    scale =
                                        1f

                                    offsetX =
                                        0f

                                    offsetY =
                                        0f

                                    onZoomedChange(
                                        false
                                    )
                                } else {
                                    val maxOffsetX =
                                        viewportSize.width *
                                            (
                                                newScale -
                                                    1f
                                                ) /
                                            2f

                                    val maxOffsetY =
                                        viewportSize.height *
                                            (
                                                newScale -
                                                    1f
                                                ) /
                                            2f

                                    scale =
                                        newScale

                                    offsetX =
                                        (
                                            offsetX +
                                                panChange.x
                                            ).coerceIn(
                                            -maxOffsetX,
                                            maxOffsetX
                                        )

                                    offsetY =
                                        (
                                            offsetY +
                                                panChange.y
                                            ).coerceIn(
                                            -maxOffsetY,
                                            maxOffsetY
                                        )

                                    onZoomedChange(
                                        true
                                    )
                                }

                                event.changes
                                    .forEach {
                                            change ->

                                        if (
                                            change.pressed
                                        ) {
                                            change.consume()
                                        }
                                    }
                            }
                        } while (
                            event.changes
                                .any {
                                    it.pressed
                                }
                        )

                        if (
                            transformGesture
                        ) {
                            if (
                                scale <
                                snapToOneThreshold
                            ) {
                                scale =
                                    1f

                                offsetX =
                                    0f

                                offsetY =
                                    0f

                                onZoomedChange(
                                    false
                                )
                            } else {
                                onZoomedChange(
                                    true
                                )
                            }
                        }
                    }
                }
                .pointerInput(
                    mediaId
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
        val mediaModifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX =
                        scale

                    scaleY =
                        scale

                    translationX =
                        offsetX

                    translationY =
                        offsetY
                }

        if (
            mimeType.startsWith(
                "video/",
                ignoreCase = true
            )
        ) {
            LoopingVideoPlayer(
                uri =
                    localUri,
                active =
                    active,
                muted =
                    false,
                modifier =
                    mediaModifier
            )
        } else {
            AsyncImage(
                model =
                    localUri,
                contentDescription =
                    null,
                modifier =
                    mediaModifier,
                contentScale =
                    ContentScale.Fit
            )
        }    }
}
