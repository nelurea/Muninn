package io.github.nelurea.muninn.capture.model

data class CaptureDraft(
    val sourceType: String,
    val sourceId: String,
    val canonicalUrl: String,
    val capturedAt: String,

    val publishedAt: String? = null,
    val discoveryMode: String? = null,
    val discoveryQuery: String? = null,

    val authorId: String,
    val authorName: String,
    val authorHandle: String? = null,
    val title: String?,
    val caption: String,

    val tags: List<String>,
    val media: List<CaptureMediaDraft>
)