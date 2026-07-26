package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeScreen(
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column {

        Text("Muninn")

        Button(
            onClick = onGalleryClick
        ) {
            Text("Gallery")
        }

        Button(
            onClick = onSettingsClick
        ) {
            Text("Settings")
        }
    }
}