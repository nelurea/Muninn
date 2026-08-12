package io.github.nelurea.muninn.discovery.pixiv

import io.github.nelurea.muninn.discovery.model.ContentRestriction
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivDiscoveryMapperTest {

    @Test
    fun mapsPixivIllustrationToDiscoveryItem() {
        val source =
            PixivFollowingIllust(
                id = "12345678",
                url =
                    "https://example.com/regular.jpg",
                url_s =
                    "https://example.com/small.jpg",
                url_sm =
                    "https://example.com/medium.jpg",
                url_w =
                    "https://example.com/wide.jpg",
                title = "Example artwork",
                alt = "Example alt",
                tags =
                    listOf(
                        "tag-a",
                        "tag-b"
                    ),
                page_count = 3,
                x_restrict = 1,
                width = 1200,
                height = 800,
                author_details =
                    PixivFollowingAuthor(
                        user_id = "42",
                        user_name = "Example Artist",
                        user_account = "example"
                    ),
                is_ad_container = null
            )

        val result =
            PixivDiscoveryMapper.map(
                source
            )

        requireNotNull(
            result
        )

        assertEquals(
            DiscoverySourceId.PIXIV,
            result.source
        )

        assertEquals(
            "12345678",
            result.sourceItemId
        )

        assertEquals(
            "https://www.pixiv.net/artworks/12345678",
            result.canonicalUrl
        )

        assertEquals(
            "https://example.com/regular.jpg",
            result.previewImageUrl
        )

        assertEquals(
            "Example artwork",
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
            3,
            result.mediaCount
        )

        assertEquals(
            ContentRestriction.R18,
            result.restriction
        )
    }

    @Test
    fun excludesAdvertisementContainer() {
        val source =
            PixivFollowingIllust(
                id = null,
                url = null,
                url_s = null,
                url_sm = null,
                url_w = null,
                title = null,
                alt = null,
                tags = null,
                page_count = null,
                x_restrict = null,
                width = null,
                height = null,
                author_details = null,
                is_ad_container = 1
            )

        val result =
            PixivDiscoveryMapper.map(
                source
            )

        assertNull(
            result
        )
    }

    @Test
    fun returnsNullWhenArtworkIdIsMissing() {
        val source =
            createValidIllust(
                id = null
            )

        val result =
            PixivDiscoveryMapper.map(
                source
            )

        assertNull(
            result
        )
    }

    @Test
    fun returnsNullWhenPreviewImageIsMissing() {
        val source =
            PixivFollowingIllust(
                id = "123",
                url = null,
                url_s = null,
                url_sm = null,
                url_w = null,
                title = "Artwork",
                alt = null,
                tags = null,
                page_count = 1,
                x_restrict = 0,
                width = null,
                height = null,
                author_details = null,
                is_ad_container = null
            )

        val result =
            PixivDiscoveryMapper.map(
                source
            )

        assertNull(
            result
        )
    }

    @Test
    fun defaultsInvalidPageCountToOne() {
        val source =
            createValidIllust(
                pageCount = 0
            )

        val result =
            PixivDiscoveryMapper.map(
                source
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
            PixivDiscoveryMapper.map(
                createValidIllust(
                    xRestrict = 0
                )
            )

        val r18 =
            PixivDiscoveryMapper.map(
                createValidIllust(
                    xRestrict = 1
                )
            )

        val r18g =
            PixivDiscoveryMapper.map(
                createValidIllust(
                    xRestrict = 2
                )
            )

        val unknown =
            PixivDiscoveryMapper.map(
                createValidIllust(
                    xRestrict = null
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
            createValidIllust(
                id = "100"
            )

        val ad =
            createValidIllust(
                id = "200",
                isAdContainer = 1
            )

        val missingId =
            createValidIllust(
                id = null
            )

        val result =
            PixivDiscoveryMapper.mapAll(
                listOf(
                    valid,
                    ad,
                    missingId
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

    private fun createValidIllust(
        id: String? = "123",
        pageCount: Int? = 1,
        xRestrict: Int? = 0,
        isAdContainer: Int? = null
    ): PixivFollowingIllust {
        return PixivFollowingIllust(
            id = id,
            url =
                "https://example.com/regular.jpg",
            url_s =
                "https://example.com/small.jpg",
            url_sm =
                "https://example.com/medium.jpg",
            url_w =
                "https://example.com/wide.jpg",
            title = "Artwork",
            alt = null,
            tags = emptyList(),
            page_count = pageCount,
            x_restrict = xRestrict,
            width = 1000,
            height = 1000,
            author_details =
                PixivFollowingAuthor(
                    user_id = "42",
                    user_name = "Artist",
                    user_account = "artist"
                ),
            is_ad_container =
                isAdContainer
        )
    }
}