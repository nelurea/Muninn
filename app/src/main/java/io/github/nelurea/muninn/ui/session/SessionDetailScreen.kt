package io.github.nelurea.muninn.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            "Session not found"
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

    Column(
        modifier = Modifier.padding(16.dp)
    ) {

        Text(
            text =
                "Session ${data.session.id}"
        )

        Text(
            text =
                "Images: ${data.images.size}"
        )

        Text(
            text = formattedDate
        )
    }
}