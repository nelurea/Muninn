package io.github.nelurea.muninn.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel
) {

    val sessions by
    viewModel.sessions.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    if (sessions.isEmpty()) {

        Text(
            text = "No sessions yet.",
            modifier = Modifier.padding(16.dp)
        )

        return
    }

    LazyColumn(
        contentPadding = PaddingValues(8.dp)
    ) {

        items(sessions) { item ->

            val formattedDate =
                SimpleDateFormat(
                    "yyyy/MM/dd HH:mm",
                    Locale.getDefault()
                ).format(
                    Date(item.session.createdAt)
                )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text =
                            "Session ${item.session.id}"
                    )

                    Text(
                        text =
                            "Images: ${item.images.size}"
                    )

                    Text(
                        text = formattedDate
                    )
                }
            }
        }
    }
}