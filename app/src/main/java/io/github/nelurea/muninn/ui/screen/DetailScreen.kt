package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import coil.compose.AsyncImage
import io.github.nelurea.muninn.data.db.ImageRecord
import io.github.nelurea.muninn.data.repository.ImageRepository
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    imageId: Long,
    repository: ImageRepository,
    onBack: () -> Unit,
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

        if (image == null) {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

        } else {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                AsyncImage(
                    model = image!!.imageUri,
                    contentDescription = null,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Fit
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    Button(
                        onClick = {

                            image?.let {
                                onShare(it.imageUri)
                            }
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