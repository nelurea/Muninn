package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import java.net.URI

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
                toMediumPreviewUrl(
                    firstMedia.sourceUrl
                ),

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

    private fun toMediumPreviewUrl(
        sourceUrl: String
    ): String {
        val uri =
            runCatching {
                URI(
                    sourceUrl
                )
            }
                .getOrNull()
                ?: return sourceUrl

        if (uri.scheme != "https" ||
            uri.rawAuthority != "pbs.twimg.com" ||
            uri.rawPath?.matches(
                Regex(
                    "^/media/[^/]+$"
                )
            ) != true ||
            uri.rawFragment != null
        ) {
            return sourceUrl
        }

        val query =
            uri.rawQuery
                ?: return sourceUrl

        if (query.isEmpty() ||
            query.startsWith("&") ||
            query.endsWith("&") ||
            query.contains("&&")
        ) {
            return sourceUrl
        }

        val parameters =
            query.split(
                "&"
            )

        val keys =
            mutableSetOf<String>()

        var nameValueStart = -1
        var queryOffset = 0

        for (parameter in parameters) {
            val separatorIndex =
                parameter.indexOf(
                    '='
                )

            if (separatorIndex <= 0 ||
                separatorIndex != parameter.lastIndexOf('=') ||
                separatorIndex == parameter.lastIndex ||
                !keys.add(
                    parameter.substring(
                        0,
                        separatorIndex
                    )
                )
            ) {
                return sourceUrl
            }

            if (parameter.substring(
                    0,
                    separatorIndex
                ) == "name"
            ) {
                nameValueStart =
                    queryOffset +
                            separatorIndex +
                            1
            }

            queryOffset +=
                parameter.length +
                        1
        }

        if (!keys.contains("format") ||
            nameValueStart < 0
        ) {
            return sourceUrl
        }

        val nameValueEnd =
            query.indexOf(
                '&',
                nameValueStart
            )
                .takeIf {
                    it >= 0
                }
                ?: query.length

        val queryStart =
            sourceUrl.indexOf('?') +
                    1

        return sourceUrl.replaceRange(
            queryStart + nameValueStart,
            queryStart + nameValueEnd,
            "medium"
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
