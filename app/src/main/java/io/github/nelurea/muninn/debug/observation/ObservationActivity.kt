package io.github.nelurea.muninn.debug.observation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import io.github.nelurea.muninn.capture.ShareUrlExtractor

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
            appendLine("flags=${intent.flags}")
            appendLine("referrer=$referrer")
            appendLine()

            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                appendLine("MULTIPLE SHARE DETECTED")
                appendLine()
            }

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

            appendLine("=== STREAMS ===")

            val streams =
                intent.getParcelableArrayListExtra<Uri>(
                    Intent.EXTRA_STREAM
                )

            if (streams == null) {

                appendLine("null")

            } else {

                appendLine("count=${streams.size}")

                streams.forEachIndexed { index, uri ->

                    appendLine(
                        "stream[$index]=$uri"
                    )
                }
            }

            val clipData = intent.clipData

            appendLine()
            appendLine("=== CLIP DATA ===")

            if (clipData == null) {

                appendLine("null")

            } else {

                appendLine(
                    "itemCount=${clipData.itemCount}"
                )

                for (i in 0 until clipData.itemCount) {

                    val item =
                        clipData.getItemAt(i)

                    appendLine(
                        "item[$i].uri=${item.uri}"
                    )

                    appendLine(
                        "item[$i].text=${item.text}"
                    )
                }
            }

            val uris = linkedSetOf<Uri>()

            stream?.let {
                uris += it
            }

            streams?.let {
                uris += it
            }

            clipData?.let {
                for (i in 0 until it.itemCount) {
                    it.getItemAt(i).uri?.let { uri ->
                        uris += uri
                    }
                }
            }

            appendLine()
            appendLine("=== ASSET INSPECTION ===")

            if (uris.isEmpty()) {

                appendLine("uri=null")

            } else {

                uris.forEachIndexed { index, uri ->

                    appendLine()
                    appendLine("--- asset[$index] ---")

                    appendLine(
                        AssetInspector.inspect(
                            this@ObservationActivity,
                            uri
                        )
                    )
                }
            }

            appendLine()
            appendLine("=== CAPTURE REQUEST ===")

            appendLine(
                ShareUrlExtractor.extract(intent).toString()
            )

            appendLine()
            appendLine("=== EXTRAS ===")

            val extras = intent.extras

            if (extras == null) {

                appendLine("extras=null")

            } else {

                for (key in extras.keySet()) {

                    appendLine(
                        "$key = ${extras.get(key)}"
                    )
                }
            }
        }
    }
}