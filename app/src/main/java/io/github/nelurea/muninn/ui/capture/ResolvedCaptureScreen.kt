package io.github.nelurea.muninn.ui.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResolvedCaptureScreen(
    viewModel: ResolvedCaptureViewModel,
    onBack: () -> Unit
) {

    val captures by
    viewModel.captures.collectAsState()

    Column {

        Button(
            onClick = onBack
        ) {
            Text("Back")
        }

        LazyColumn {

            items(captures) { capture ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                    ) {

                        Text(
                            "Type: ${capture.sourceType}"
                        )

                        Text(
                            "SourceId: ${capture.sourceId}"
                        )

                        Text(
                            "ImageIndex: ${capture.imageIndex}"
                        )

                        Text(
                            "CreatedAt: ${capture.createdAt}"
                        )
                    }
                }
            }
        }
    }
}