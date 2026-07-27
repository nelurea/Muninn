package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.nelurea.muninn.data.repository.ImageRepository

@Composable
fun GalleryScreen(
    repository: ImageRepository,
    onBack: () -> Unit
) {

    val images by repository.getImages()
        .collectAsState(initial = emptyList())

    Column {

        Text("Gallery")

        LazyColumn {

            items(images) { image ->

                Text(image.imageUri)
            }
        }

        Button(
            onClick = onBack
        ) {
            Text("Back")
        }
    }
}