package io.github.nelurea.muninn.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onResolvedCapturesClick: () -> Unit
) {

    Column {

        Text("Setting")

        Button(
            onClick = onResolvedCapturesClick
        ) {
            Text("Resolved Captures")
        }

        Button(
            onClick = onBack
        ) {
            Text("Back")
        }
    }
}