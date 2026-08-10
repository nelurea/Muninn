package io.github.nelurea.muninn.ui.session

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    viewModel: SessionDetailViewModel
) {

    val session by
    viewModel.session.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(
            sessionId
        )
    }

    if (session == null) {

        Text(
            text = "Session not found",
            modifier = Modifier.padding(16.dp)
        )

        return
    }

    val data = session!!

    val formattedDate =
        SimpleDateFormat(
            "yyyy/MM/dd HH:mm",
            Locale.getDefault()
        ).format(
            Date(
                data.session.createdAt
            )
        )

    val totalImageCount =
        data.images.size +
                data.capturedWorks.sumOf {
                    it.media.size
                }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.padding(16.dp),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Text(
                text =
                    "Session ${data.session.id}"
            )
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Text(
                text =
                    "Images: $totalImageCount"
            )
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Text(
                text = formattedDate
            )
        }

        if (data.images.isNotEmpty()) {

            item(
                span = {
                    GridItemSpan(maxLineSpan)
                }
            ) {
                Text(
                    text = "Images"
                )
            }

            items(data.images) { image ->

                Image(
                    painter =
                        rememberAsyncImagePainter(
                            model = Uri.parse(
                                image.imageUri
                            )
                        ),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale =
                        ContentScale.Crop
                )
            }
        }

        data.capturedWorks
            .sortedBy {
                it.work.id
            }
            .forEach { capturedWork ->

                item(
                    span = {
                        GridItemSpan(maxLineSpan)
                    }
                ) {

                    val title =
                        capturedWork.work.title
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: capturedWork.work.authorName

                    Text(
                        text =
                            "$title · ${capturedWork.work.sourceType}"
                    )
                }

                items(
                    capturedWork.media
                        .sortedBy {
                            it.mediaIndex
                        }
                ) { media ->

                    Image(
                        painter =
                            rememberAsyncImagePainter(
                                model =
                                    Uri.parse(
                                        media.localUri
                                    )
                            ),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale =
                            ContentScale.Crop
                    )
                }
            }
    }
}
