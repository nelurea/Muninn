package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.discovery.ArtworkPreviewSource
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

class XArtworkPreviewSource(
    private val observationStore:
    XDiscoveryObservationStore =
        XDiscoveryObservationStore
) : ArtworkPreviewSource {

    override suspend fun load(
        item: DiscoveryItem
    ): ArtworkPreview {

        require(
            item.source ==
                    DiscoverySourceId.X
        ) {
            "XArtworkPreviewSource only supports X items."
        }

        val payload =
            observationStore
                .findBySourceId(
                    item.sourceItemId
                )
                ?: throw XDiscoveryException(
                    "Observed X post is no longer available: ${item.sourceItemId}"
                )

        return ArtworkPreview(
            source =
                DiscoverySourceId.X,

            sourceItemId =
                payload.sourceId,

            canonicalUrl =
                payload.canonicalUrl,

            publishedAt =
                payload.publishedAt,

            title =
                payload.title,

            caption =
                payload.caption,

            creator =
                DiscoveryCreator(
                    sourceCreatorId =
                        payload.authorId,

                    name =
                        buildCreatorLabel(
                            authorName =
                                payload.authorName,
                            authorHandle =
                                payload.authorHandle
                        )
                ),

            creatorAvatarUrl =
                null,

            tags =
                payload.tags,

            media =
                payload.media
                    .sortedBy {
                        it.mediaIndex
                    }
                    .map {
                            media ->

                        ArtworkPreviewMedia(
                            mediaIndex =
                                media.mediaIndex,

                            previewUrl =
                                media.previewUrl
                                    ?: media.sourceUrl,

                            originalUrl =
                                media.sourceUrl,

                            mimeType =
                                media.mimeType,

                            playbackUri =
                                media.sourceUrl
                                    .takeIf {
                                        media.mimeType
                                            ?.startsWith(
                                                "video/",
                                                ignoreCase = true
                                            ) ==
                                            true
                                    }
                        )
                    }
        )
    }

    private fun buildCreatorLabel(
        authorName: String?,
        authorHandle: String?
    ): String? {
        val name =
            authorName
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val handle =
            authorHandle
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        return when {
            name != null &&
                    handle != null -> {

                "$name @$handle"
            }

            name != null ->
                name

            handle != null ->
                "@$handle"

            else ->
                null
        }
    }
}
