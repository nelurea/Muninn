package io.github.nelurea.muninn.capture

data class ResolvedCapture(
    val sourceType: SourceType,
    val sourceId: String,
    val imageIndex: Int?
)