package io.github.nelurea.muninn.capture.web.pixiv

data class PixivCapturePayload(
    val sourceType: String,
    val sourceId: String,
    val canonicalUrl: String,
    val capturedAt: String,

    val authorId: String?,
    val authorName: String?,
    val title: String?,
    val caption: String?,

    val tags: List<String>,
    val media: List<PixivCaptureMediaPayload>
)

data class PixivCaptureMediaPayload(
    val mediaIndex: Int,
    val sourceUrl: String,
    val mimeType: String?,
    val fileName: String
)