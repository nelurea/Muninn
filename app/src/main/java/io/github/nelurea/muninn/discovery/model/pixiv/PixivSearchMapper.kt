package io.github.nelurea.muninn.discovery.pixiv

import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

object PixivSearchMapper {

    fun map(
        illust: PixivSearchIllust
    ): DiscoveryItem? {
        if (
            illust.isAdContainer
        ) {
            return null
        }

        val id =
            illust.id
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val previewImageUrl =
            illust.url
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return null

        return DiscoveryItem(
            source =
                DiscoverySourceId.PIXIV,

            sourceItemId =
                id,

            canonicalUrl =
                "https://www.pixiv.net/artworks/$id",

            previewImageUrl =
                previewImageUrl,

            title =
                illust.title
                    ?.takeIf {
                        it.isNotBlank()
                    },

            creator =
                mapCreator(
                    userId =
                        illust.userId,
                    userName =
                        illust.userName
                ),

            mediaCount =
                illust.pageCount
                    ?.takeIf {
                        it > 0
                    }
                    ?: 1,

            restriction =
                mapRestriction(
                    illust.xRestrict
                )
        )
    }

    fun mapAll(
        illusts: List<PixivSearchIllust>
    ): List<DiscoveryItem> {
        return illusts.mapNotNull(
            ::map
        )
    }

    private fun mapCreator(
        userId: String?,
        userName: String?
    ): DiscoveryCreator? {
        val id =
            userId
                ?.takeIf {
                    it.isNotBlank()
                }

        val name =
            userName
                ?.takeIf {
                    it.isNotBlank()
                }

        if (
            id == null &&
            name == null
        ) {
            return null
        }

        return DiscoveryCreator(
            sourceCreatorId =
                id,
            name =
                name
        )
    }

    private fun mapRestriction(
        xRestrict: Int?
    ): ContentRestriction {
        return when (
            xRestrict
        ) {
            0 ->
                ContentRestriction.GENERAL

            1 ->
                ContentRestriction.R18

            2 ->
                ContentRestriction.R18G

            else ->
                ContentRestriction.UNKNOWN
        }
    }
}