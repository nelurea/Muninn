package io.github.nelurea.muninn.ui.capture

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CapturedWorkDetailScreen(
    workId: Long,
    repository: CapturedWorkRepository
) {
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Captured Work"
                    )
                }
            )
        },
        floatingActionButton = {
            if (
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

        when {
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
                                        )
                            ) { page ->

                                val pageMedia =
                                    media[page]

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                ) {
                                    AsyncImage(
                                        model =
                                            pageMedia
                                                .localUri,
                                        contentDescription =
                                            null,
                                        modifier =
                                            Modifier
                                                .fillMaxSize(),
                                        contentScale =
                                            ContentScale.Fit
                                    )

                                    if (
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

                            if (
                                media.size > 1
                            ) {
                                Text(
                                    text =
                                        "${pagerState.currentPage + 1} / ${media.size}",
                                    modifier =
                                        Modifier
                                            .align(
                                                Alignment
                                                    .CenterHorizontally
                                            )
                                            .padding(
                                                vertical =
                                                    12.dp
                                            ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                )
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