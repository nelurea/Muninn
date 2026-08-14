package io.github.nelurea.muninn.capture.web.x

data class XCapturePayload(
    val sourceType: String,
    val sourceId: String,
    val canonicalUrl: String,
    val capturedAt: String,
    val publishedAt: String? = null,

    val authorId: String?,
    val authorName: String?,
    val authorHandle: String?,

    val title: String?,
    val caption: String?,

    val tags: List<String>,
    val media: List<XCaptureMediaPayload>
)

data class XCaptureMediaPayload(
    val mediaIndex: Int,
    val sourceUrl: String,
    val mimeType: String?,
    val fileName: String
)