package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import coil.compose.AsyncImage
import io.github.nelurea.muninn.data.db.ImageRecord
import io.github.nelurea.muninn.data.repository.ImageRepository
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    imageId: Long,
    repository: ImageRepository,
    onBack: () -> Unit
) {

    var image by remember {
        mutableStateOf<ImageRecord?>(null)
    }

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

            AsyncImage(
                model = image!!.imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}