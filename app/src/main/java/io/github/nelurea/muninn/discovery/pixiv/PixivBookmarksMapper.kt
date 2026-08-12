package io.github.nelurea.muninn.discovery.pixiv

import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

object PixivBookmarksMapper {

    fun map(
        bookmark: PixivBookmarkIllust
    ): DiscoveryItem? {
        val id =
            bookmark.id
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val previewImageUrl =
            sequenceOf(
                bookmark.url,
                bookmark.url_w,
                bookmark.url_sm,
                bookmark.url_s
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
                bookmark.title
                    ?.takeIf {
                        it.isNotBlank()
                    },

            creator =
                mapCreator(
                    bookmark.author_details
                ),

            mediaCount =
                bookmark.page_count
                    ?.takeIf {
                        it > 0
                    }
                    ?: 1,

            restriction =
                when (
                    bookmark.x_restrict
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
        )
    }

    fun mapAll(
        bookmarks: List<PixivBookmarkIllust>
    ): List<DiscoveryItem> {
        return bookmarks.mapNotNull(
            ::map
        )
    }

    private fun mapCreator(
        author: PixivFollowingAuthor?
    ): DiscoveryCreator? {
        if (
            author == null
        ) {
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
}