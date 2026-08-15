package io.github.nelurea.muninn.discovery.model

data class DiscoveryItem(
    val source: DiscoverySourceId,
    val sourceItemId: String,
    val canonicalUrl: String,
    val previewImageUrl: String,
    val title: String?,
    val creator: DiscoveryCreator?,
    val mediaCount: Int,
    val restriction: ContentRestriction
)

data class DiscoveryCreator(
    val sourceCreatorId: String?,
    val name: String?
)

enum class DiscoverySourceId {
    PIXIV,
    X,
}

enum class ContentRestriction {
    GENERAL,
    R18,
    R18G,
    UNKNOWN
}