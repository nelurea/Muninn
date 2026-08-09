package io.github.nelurea.muninn.capturepackage

data class CapturePackage(
    val schemaVersion: Int,
    val source: CapturePackageSource,
    val capturedAt: String,
    val content: CapturePackageContent,
    val media: List<CapturePackageMedia>
)

data class CapturePackageSource(
    val type: String,
    val id: String,
    val canonicalUrl: String
)

data class CapturePackageContent(
    val author: CapturePackageAuthor,
    val title: String?,
    val caption: String,
    val tags: List<String>
)

data class CapturePackageAuthor(
    val id: String,
    val name: String
)

data class CapturePackageMedia(
    val index: Int,
    val sourceUrl: String,
    val mimeType: String,
    val fileName: String
)