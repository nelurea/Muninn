package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.nelurea.muninn.data.db.ImageRecord
import io.github.nelurea.muninn.data.repository.ImageRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    imageId: Long,
    repository: ImageRepository,
    onDelete: () -> Unit,
    onShare: (String) -> Unit
) {

    var image by remember {
        mutableStateOf<ImageRecord?>(null)
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(imageId) {
        image = repository.getImage(imageId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Image Detail")
                }
            )
        }
    ) { _ ->

        val currentImage = image

        if (currentImage == null) {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

        } else {

            val formattedDate = remember(currentImage.createdAt) {
                SimpleDateFormat(
                    "yyyy/MM/dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(currentImage.createdAt))
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                AsyncImage(
                    model = currentImage.imageUri,
                    contentDescription = null,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Fit
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = "ID: ${currentImage.id}"
                    )

                    Text(
                        text = "Saved: $formattedDate"
                    )

                    Text(
                        text = "URI:"
                    )

                    Text(
                        text = currentImage.imageUri
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Button(
                        onClick = {
                            onShare(currentImage.imageUri)
                        }
                    ) {
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                repository.deleteImage(imageId)
                                onDelete()
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}