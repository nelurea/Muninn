package io.github.nelurea.muninn.ui.screen

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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository

@Composable
fun GalleryScreen(
    repository: CapturedWorkRepository,
    onWorkClick: (Long) -> Unit
) {
    var works by remember {
        mutableStateOf<List<CapturedWorkWithMedia>>(
            emptyList()
        )
    }

    LaunchedEffect(Unit) {
        works =
            repository
                .getAllWithMedia()
    }

    LazyColumn {
        items(
            items = works,
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
                                item.work.id
                            )
                        }
                        .padding(
                            bottom = 20.dp
                        )
            ) {
                coverMedia
                    ?.let {
                            media ->

                        AsyncImage(
                            model =
                                media.localUri,
                            contentDescription =
                                null,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                            contentScale =
                                ContentScale.Fit
                        )
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
                        ?.let {
                            Text(
                                text = it,
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
