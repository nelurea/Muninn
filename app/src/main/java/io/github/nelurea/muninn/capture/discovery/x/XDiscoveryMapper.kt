package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

object XDiscoveryMapper {

    fun map(
        payload: XCapturePayload
    ): DiscoveryItem {
        val firstMedia =
            payload.media
                .minByOrNull {
                    it.mediaIndex
                }
                ?: error(
                    "X Discovery item has no media."
                )

        return DiscoveryItem(
            source =
                DiscoverySourceId.X,

            sourceItemId =
                payload.sourceId,

            canonicalUrl =
                payload.canonicalUrl,

            previewImageUrl =
                firstMedia.sourceUrl,

            title =
                payload.caption
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    },

            creator =
                DiscoveryCreator(
                    sourceCreatorId =
                        payload.authorId,

                    name =
                        buildCreatorLabel(
                            payload
                        )
                ),

            mediaCount =
                payload.media.size,

            restriction =
                ContentRestriction.UNKNOWN
        )
    }

    fun mapAll(
        payloads: List<XCapturePayload>
    ): List<DiscoveryItem> {
        return payloads.mapNotNull {
                payload ->

            runCatching {
                map(
                    payload
                )
            }
                .getOrNull()
        }
    }

    private fun buildCreatorLabel(
        payload: XCapturePayload
    ): String? {
        val name =
            payload.authorName
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val handle =
            payload.authorHandle
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