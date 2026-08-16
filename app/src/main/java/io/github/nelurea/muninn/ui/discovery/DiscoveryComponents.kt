package io.github.nelurea.muninn.ui.discovery

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.discovery.DiscoverySaveQueueUiState
import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

@Composable
fun DiscoverySaveStatusBar(
    state: DiscoverySaveQueueUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message =
        state.message
            ?: return

    Surface(
        modifier =
            modifier,
        shape =
            RoundedCornerShape(
                12.dp
            ),
        tonalElevation =
            6.dp,
        shadowElevation =
            6.dp
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            if (
                state.activeCount > 0
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(
                            20.dp
                        ),
                    strokeWidth =
                        2.dp
                )
            }

            Text(
                text =
                    message,
                modifier =
                    Modifier.weight(
                        1f,
                        fill =
                            false
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            if (
                state.failedRequestKey != null
            ) {
                OutlinedButton(
                    onClick =
                        onRetry
                ) {
                    Text(
                        "Retry"
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoverySourceButton(
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
fun DiscoveryModeButton(
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
fun DiscoveryGridItem(
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
                        "▧ ${item.mediaCount}",
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

fun sourceLabel(
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

fun sourceReferer(
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

fun shouldLoadMore(
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
