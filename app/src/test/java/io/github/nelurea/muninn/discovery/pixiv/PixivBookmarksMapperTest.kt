package io.github.nelurea.muninn.discovery.pixiv

import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivBookmarksMapperTest {

    @Test
    fun mapsBookmarkToDiscoveryItem() {
        val bookmark =
            PixivBookmarkIllust(
                id =
                    "123456",

                url =
                    "https://example.com/regular.jpg",

                url_s =
                    "https://example.com/small.jpg",

                url_sm =
                    "https://example.com/medium.jpg",

                url_w =
                    "https://example.com/wide.jpg",

                title =
                    "Example bookmark",

                alt =
                    "Example alt",

                tags =
                    listOf(
                        "tag-a",
                        "tag-b"
                    ),

                page_count =
                    13,

                x_restrict =
                    1,

                width =
                    1280,

                height =
                    1280,

                author_details =
                    PixivFollowingAuthor(
                        user_id =
                            "42",

                        user_name =
                            "Example Artist",

                        user_account =
                            "example"
                    )
            )

        val result =
            PixivBookmarksMapper.map(
                bookmark
            )

        requireNotNull(
            result
        )

        assertEquals(
            DiscoverySourceId.PIXIV,
            result.source
        )

        assertEquals(
            "123456",
            result.sourceItemId
        )

        assertEquals(
            "https://www.pixiv.net/artworks/123456",
            result.canonicalUrl
        )

        assertEquals(
            "https://example.com/regular.jpg",
            result.previewImageUrl
        )

        assertEquals(
            "Example bookmark",
            result.title
        )

        assertEquals(
            "42",
            result.creator
                ?.sourceCreatorId
        )

        assertEquals(
            "Example Artist",
            result.creator
                ?.name
        )

        assertEquals(
            13,
            result.mediaCount
        )

        assertEquals(
            ContentRestriction.R18,
            result.restriction
        )
    }

    @Test
    fun returnsNullWhenIdIsMissing() {
        val bookmark =
            createValidBookmark(
                id =
                    null
            )

        val result =
            PixivBookmarksMapper.map(
                bookmark
            )

        assertNull(
            result
        )
    }

    @Test
    fun returnsNullWhenPreviewImageIsMissing() {
        val bookmark =
            PixivBookmarkIllust(
                id =
                    "123",

                url =
                    null,

                url_s =
                    null,

                url_sm =
                    null,

                url_w =
                    null,

                title =
                    "Artwork",

                alt =
                    null,

                tags =
                    null,

                page_count =
                    1,

                x_restrict =
                    0,

                width =
                    null,

                height =
                    null,

                author_details =
                    null
            )

        val result =
            PixivBookmarksMapper.map(
                bookmark
            )

        assertNull(
            result
        )
    }

    @Test
    fun defaultsInvalidPageCountToOne() {
        val bookmark =
            createValidBookmark(
                pageCount =
                    0
            )

        val result =
            PixivBookmarksMapper.map(
                bookmark
            )

        requireNotNull(
            result
        )

        assertEquals(
            1,
            result.mediaCount
        )
    }

    @Test
    fun mapsRestrictions() {
        val general =
            PixivBookmarksMapper.map(
                createValidBookmark(
                    xRestrict =
                        0
                )
            )

        val r18 =
            PixivBookmarksMapper.map(
                createValidBookmark(
                    xRestrict =
                        1
                )
            )

        val r18g =
            PixivBookmarksMapper.map(
                createValidBookmark(
                    xRestrict =
                        2
                )
            )

        val unknown =
            PixivBookmarksMapper.map(
                createValidBookmark(
                    xRestrict =
                        null
                )
            )

        assertEquals(
            ContentRestriction.GENERAL,
            general?.restriction
        )

        assertEquals(
            ContentRestriction.R18,
            r18?.restriction
        )

        assertEquals(
            ContentRestriction.R18G,
            r18g?.restriction
        )

        assertEquals(
            ContentRestriction.UNKNOWN,
            unknown?.restriction
        )
    }

    @Test
    fun mapsAllAndDropsInvalidEntries() {
        val valid =
            createValidBookmark(
                id =
                    "100"
            )

        val missingId =
            createValidBookmark(
                id =
                    null
            )

        val missingImage =
            PixivBookmarkIllust(
                id =
                    "300",

                url =
                    null,

                url_s =
                    null,

                url_sm =
                    null,

                url_w =
                    null,

                title =
                    null,

                alt =
                    null,

                tags =
                    null,

                page_count =
                    null,

                x_restrict =
                    null,

                width =
                    null,

                height =
                    null,

                author_details =
                    null
            )

        val result =
            PixivBookmarksMapper.mapAll(
                listOf(
                    valid,
                    missingId,
                    missingImage
                )
            )

        assertEquals(
            1,
            result.size
        )

        assertTrue(
            result.any {
                it.sourceItemId ==
                        "100"
            }
        )
    }

    private fun createValidBookmark(
        id: String? =
            "123",

        pageCount: Int? =
            1,

        xRestrict: Int? =
            0
    ): PixivBookmarkIllust {
        return PixivBookmarkIllust(
            id =
                id,

            url =
                "https://example.com/regular.jpg",

            url_s =
                "https://example.com/small.jpg",

            url_sm =
                "https://example.com/medium.jpg",

            url_w =
                "https://example.com/wide.jpg",

            title =
                "Artwork",

            alt =
                null,

            tags =
                emptyList(),

            page_count =
                pageCount,

            x_restrict =
                xRestrict,

            width =
                1000,

            height =
                1000,

            author_details =
                PixivFollowingAuthor(
                    user_id =
                        "42",

                    user_name =
                        "Artist",

                    user_account =
                        "artist"
                )
        )
    }
}