package io.github.nelurea.muninn.capture

data class CaptureRequest(
    val sourceUrl: String,
    val imageIndex: Int? = null
)