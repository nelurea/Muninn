package io.github.nelurea.muninn.discovery.pixiv

import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

object PixivDiscoveryMapper {

    fun map(
        illust: PixivFollowingIllust
    ): DiscoveryItem? {
        if (
            illust.is_ad_container != null &&
            illust.is_ad_container != 0
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
            sequenceOf(
                illust.url,
                illust.url_w,
                illust.url_sm,
                illust.url_s
            )
                .filterNotNull()
                .firstOrNull {
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
                    illust.author_details
                ),

            mediaCount =
                illust.page_count
                    ?.takeIf {
                        it > 0
                    }
                    ?: 1,

            restriction =
                mapRestriction(
                    illust.x_restrict
                )
        )
    }

    fun mapAll(
        illusts: List<PixivFollowingIllust>
    ): List<DiscoveryItem> {
        return illusts.mapNotNull(
            ::map
        )
    }

    private fun mapCreator(
        author: PixivFollowingAuthor?
    ): DiscoveryCreator? {
        if (author == null) {
            return null
        }

        val id =
            author.user_id
                ?.takeIf {
                    it.isNotBlank()
                }

        val name =
            author.user_name
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