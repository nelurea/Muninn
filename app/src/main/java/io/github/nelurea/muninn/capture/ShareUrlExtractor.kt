package io.github.nelurea.muninn.capture

import android.content.Intent
import android.net.Uri

object ShareUrlExtractor {

    private val urlPattern = Regex("""https?://[^\s]+""")
    private val imageIndexPattern = Regex("""(?:big_|photo/)(\d+)""")

    fun extract(intent: Intent): CaptureRequest? {

        if (intent.action != Intent.ACTION_SEND) {
            return null
        }

        val text =
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: return null

        val rawUrl =
            urlPattern.find(text)
                ?.value
                ?: return null

        val parsed =
            runCatching {
                Uri.parse(rawUrl)
            }.getOrNull()
                ?: return null

        val imageIndex =
            parsed.fragment
                ?.let(::extractImageIndex)

        val normalizedUrl =
            parsed.buildUpon()
                .fragment(null)
                .build()
                .toString()

        return CaptureRequest(
            sourceUrl = normalizedUrl,
            imageIndex = imageIndex
        )
    }

    private fun extractImageIndex(fragment: String): Int? {
        return imageIndexPattern.find(fragment)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}