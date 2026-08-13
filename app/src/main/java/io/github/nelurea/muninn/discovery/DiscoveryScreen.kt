package io.github.nelurea.muninn.ui.discovery

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.discovery.DiscoveryViewModel
import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
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

    Column(
        modifier =
            modifier.fillMaxSize()
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
                                    top =
                                        16.dp
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
                                    onItemClick(
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
                                                top =
                                                    8.dp
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
                    horizontal =
                        7.dp,
                    vertical =
                        3.dp
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
                            alpha =
                                0.72f
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
                            top =
                                6.dp
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
                            top =
                                2.dp
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