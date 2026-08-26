package io.github.nelurea.muninn.ui.screen

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.ui.media.LoopingVideoPlayer

@Composable
fun GalleryScreen(
    repository: CapturedWorkRepository,
    onWorkClick: (Long, String?) -> Unit
) {
    val context =
        LocalContext.current

    var works by remember {
        mutableStateOf<
            List<CapturedWorkWithMedia>
        >(
            emptyList()
        )
    }

    LaunchedEffect(
        Unit
    ) {
        works =
            repository
                .getAllWithMedia()
    }

    LazyColumn {
        items(
            items =
                works,
            key = {
                it.work.id
            }
        ) {
                item ->

            val coverMedia =
                item.media
                    .minByOrNull {
                        it.mediaIndex
                    }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
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
                        .padding(
                            bottom = 20.dp
                        )
            ) {
                coverMedia
                    ?.let {
                            media ->

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
                                onError = {
                                        state ->

                                    Log.e(
                                        "Muninn/Gallery",
                                        "Failed to load ${media.localUri}",
                                        state
                                            .result
                                            .throwable
                                    )
                                }
                            )
                        }                    }

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
                        ?.let {
                                title ->

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
