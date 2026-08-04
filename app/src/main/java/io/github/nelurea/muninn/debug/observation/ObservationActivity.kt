package io.github.nelurea.muninn.debug.observation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

class ObservationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = buildReport(intent)

        setContent {
            Text(
                text = report,
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                )
            )
        }
    }

    private fun buildReport(intent: Intent?): String {

        if (intent == null) {
            return "intent=null"
        }

        return buildString {

            appendLine("=== SHARE OBSERVATION ===")
            appendLine()

            appendLine("action=${intent.action}")
            appendLine("type=${intent.type}")
            appendLine()

            appendLine(
                "EXTRA_TEXT=${
                    intent.getStringExtra(
                        Intent.EXTRA_TEXT
                    )
                }"
            )

            appendLine(
                "EXTRA_SUBJECT=${
                    intent.getStringExtra(
                        Intent.EXTRA_SUBJECT
                    )
                }"
            )

            appendLine()

            val stream =
                intent.getParcelableExtra<Uri>(
                    Intent.EXTRA_STREAM
                )

            appendLine("EXTRA_STREAM=$stream")
            appendLine()

            appendLine("=== EXTRAS ===")

            intent.extras?.keySet()?.forEach { key ->

                appendLine(
                    "$key = ${intent.extras?.get(key)}"
                )
            }
        }
    }
}