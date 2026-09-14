package io.github.nelurea.muninn.discovery.model

import io.github.nelurea.muninn.content.ContentRestriction

data class ArtworkPreview(
    val source: DiscoverySourceId,
    val sourceItemId: String,
    val canonicalUrl: String,

    val publishedAt: String?,

    val title: String?,
    val caption: String?,

    val creator: DiscoveryCreator?,
    val creatorAvatarUrl: String?,

    val tags: List<String>,
    val restriction: ContentRestriction = ContentRestriction.UNKNOWN,

    val media: List<ArtworkPreviewMedia>
)

data class ArtworkPreviewMedia(
    val mediaIndex: Int,
    val previewUrl: String,
    val originalUrl: String,
    val mimeType: String? = null,
    val playbackUri: String? = null
)