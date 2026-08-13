package io.github.nelurea.muninn.discovery.model

data class ArtworkPreview(
    val source: DiscoverySourceId,
    val sourceItemId: String,
    val canonicalUrl: String,

    val title: String?,
    val caption: String?,

    val creator: DiscoveryCreator?,
    val creatorAvatarUrl: String?,

    val tags: List<String>,

    val media: List<ArtworkPreviewMedia>
)

data class ArtworkPreviewMedia(
    val mediaIndex: Int,
    val previewUrl: String,
    val originalUrl: String
)