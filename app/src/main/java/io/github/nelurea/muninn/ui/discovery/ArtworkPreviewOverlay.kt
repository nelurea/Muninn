package io.github.nelurea.muninn.ui.discovery

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.R
import io.github.nelurea.muninn.discovery.ArtworkPreviewUiState
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import kotlin.math.abs
import androidx.compose.material3.Icon

@Composable
fun ArtworkPreviewOverlay(
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

    var dismissDragOffsetY by remember {
        mutableFloatStateOf(
            0f
        )
    }

    val dismissFadeDistance =
        with(
            LocalDensity.current
        ) {
            240.dp.toPx()
        }

    val previewAlpha =
        (
                1f -
                        abs(
                            dismissDragOffsetY
                        ) /
                        dismissFadeDistance
                ).coerceIn(
                0.35f,
                1f
            )

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .alpha(
                    previewAlpha
                ),
        color =
            Color.Black.copy(
                alpha = 0.94f
            )
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.align(
                                Alignment.Center
                            )
                    )

                    IconButton(
                        onClick =
                            onClose,
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopStart
                                )
                                .padding(
                                    12.dp
                                )
                    ) {
                        Text(
                            text =
                                "×",
                            color =
                                Color.White,
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineMedium
                        )
                    }
                }
            }

            state.error != null -> {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier =
                            Modifier
                                .align(
                                    Alignment.Center
                                )
                                .padding(
                                    24.dp
                                ),
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
                    }

                    IconButton(
                        onClick =
                            onClose,
                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopStart
                                )
                                .padding(
                                    12.dp
                                )
                    ) {
                        Text(
                            text =
                                "×",
                            color =
                                Color.White,
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineMedium
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
                            true
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
                            onDismiss =
                                onClose,
                            onDismissDrag = {
                                    offset ->

                                dismissDragOffsetY =
                                    offset
                            },
                            onDismissDragReset = {
                                dismissDragOffsetY =
                                    0f
                            },
                            onToggleControls = {
                                controlsVisible =
                                    !controlsVisible
                            }
                        )
                    }

                    val currentMedia =
                        preview.media[
                            pagerState.currentPage
                        ]

                    val selected =
                        currentMedia.mediaIndex in
                                state.selectedMediaIndices

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
                            IconButton(
                                onClick =
                                    onClose
                            ) {
                                Text(
                                    text =
                                        "×",
                                    color =
                                        Color.White,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .headlineMedium
                                )
                            }

                            OutlinedButton(
                                onClick =
                                    onOpenOriginal
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

                        ArtworkPreviewInfoPanel(
                            preview =
                                preview,
                            selectedCount =
                                state
                                    .selectedMediaIndices
                                    .size,
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
                                    .padding(
                                        end = 84.dp
                                    )
                        )
                    }

                    ArtworkSelectionButton(
                        selected =
                            selected,
                        onClick = {
                            if (
                                selected
                            ) {
                                onDeselect(
                                    currentMedia.mediaIndex
                                )
                            } else {
                                onSelect(
                                    currentMedia.mediaIndex
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .align(
                                    Alignment.BottomEnd
                                )
                                .padding(
                                    end = 16.dp,
                                    bottom = 24.dp
                                )
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkSelectionButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (
        selected
    ) {
        Button(
            onClick =
                onClick,
            modifier =
                modifier.size(
                    64.dp
                ),
            shape =
                CircleShape,
            contentPadding =
                PaddingValues(
                    0.dp
                )
        ) {
            Text(
                text =
                    "✓",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }
    } else {
        OutlinedButton(
            onClick =
                onClick,
            modifier =
                modifier.size(
                    64.dp
                ),
            shape =
                CircleShape,
            contentPadding =
                PaddingValues(
                    0.dp
                )
        ) {
            Text(
                text =
                    "✓",
                color =
                    Color.White,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ArtworkPreviewInfoPanel(
    preview: ArtworkPreview,
    selectedCount: Int,
    onTagClick: (String) -> Unit,
    onSaveAll: () -> Unit,
    onSaveSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context =
        LocalContext.current

    var detailsExpanded by rememberSaveable(
        preview.sourceItemId
    ) {
        mutableStateOf(
            false
        )
    }

    val hasDetails =
        !preview.caption
            .isNullOrBlank() ||
            preview.tags.isNotEmpty()

    val panelShape =
        RoundedCornerShape(
            14.dp
        )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    12.dp
                )
                .border(
                    width =
                        1.dp,
                    color =
                        Color.White.copy(
                            alpha = 0.12f
                        ),
                    shape =
                        panelShape
                ),
        color =
            Color.Black.copy(
                alpha = 0.70f
            ),
        shape =
            panelShape
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
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled =
                                hasDetails
                        ) {
                            detailsExpanded =
                                !detailsExpanded
                        },
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
                                2,
                            overflow =
                                TextOverflow.Ellipsis
                        )
                    }
            }

            if (
                detailsExpanded
            ) {
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
                    !preview.caption.isNullOrBlank() &&
                    preview.tags.isNotEmpty()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                10.dp
                            )
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
                                            horizontal = 10.dp,
                                            vertical = 5.dp
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
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.End,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                IconButton(
                    onClick =
                        onSaveAll
                ) {
                    Icon(
                        painter =
                            painterResource(
                                R.drawable.ic_save_all
                            ),
                        contentDescription =
                            "Save all",
                        tint =
                            Color.White
                    )
                }

                if (
                    preview.media.size > 1
                ) {
                    IconButton(
                        onClick =
                            onSaveSelected,
                        enabled =
                            selectedCount > 0
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    R.drawable.ic_save_selected
                                ),
                            contentDescription =
                                "Save selected",
                            tint =
                                if (
                                    selectedCount > 0
                                ) {
                                    Color.White
                                } else {
                                    Color.White.copy(
                                        alpha = 0.35f
                                    )
                                }
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
    onDismiss: () -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissDragReset: () -> Unit,
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

    val dismissThreshold =
        with(
            LocalDensity.current
        ) {
            72.dp.toPx()
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

                            onDismissDrag(
                                0f
                            )
                        },
                        onVerticalDrag = {
                                change,
                                dragAmount ->

                            verticalDragTotal +=
                                dragAmount

                            onDismissDrag(
                                verticalDragTotal
                            )

                            change.consume()
                        },
                        onDragEnd = {
                            if (
                                abs(
                                    verticalDragTotal
                                ) >=
                                dismissThreshold
                            ) {
                                onDismiss()
                            } else {
                                onDismissDragReset()
                            }

                            verticalDragTotal =
                                0f
                        },
                        onDragCancel = {
                            verticalDragTotal =
                                0f

                            onDismissDragReset()
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
