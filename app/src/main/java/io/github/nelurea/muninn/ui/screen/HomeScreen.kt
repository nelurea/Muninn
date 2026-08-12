package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeScreen(
    onGalleryClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onWebCaptureClick: () -> Unit,
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
            onClick = onWebCaptureClick
        ) {
            Text("Web Capture")
        }

        Button(
            onClick = onSettingsClick
        ) {
            Text("Settings")
        }
        Button(
            onClick =
                onDiscoveryClick
        ) {
            Text(
                "Discovery"
            )
        }
    }
}