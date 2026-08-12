package io.github.nelurea.muninn.discovery.pixiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivFollowingParserTest {

    @Test
    fun parsesFollowingResponse() {
        val json =
            """
            {
              "error": false,
              "message": "",
              "body": {
                "illusts": [
                  {
                    "id": "123456",
                    "url": "https://example.com/regular.jpg",
                    "url_s": "https://example.com/small.jpg",
                    "url_sm": "https://example.com/medium.jpg",
                    "url_w": "https://example.com/wide.jpg",
                    "title": "Example artwork",
                    "alt": "Example alt",
                    "tags": [
                      "tag-a",
                      "tag-b"
                    ],
                    "page_count": 3,
                    "x_restrict": 1,
                    "width": 1200,
                    "height": 900,
                    "author_details": {
                      "user_id": "42",
                      "user_name": "Example Artist",
                      "user_account": "example"
                    }
                  },
                  {
                    "is_ad_container": 1
                  }
                ],
                "total": 100,
                "lastPage": false
              }
            }
            """.trimIndent()

        val result =
            PixivFollowingParser.parse(
                json
            )

        assertFalse(
            result.error
        )

        assertNull(
            result.message
        )

        val body =
            requireNotNull(
                result.body
            )

        assertEquals(
            100,
            body.total
        )

        assertEquals(
            false,
            body.lastPage
        )

        val illusts =
            requireNotNull(
                body.illusts
            )

        assertEquals(
            2,
            illusts.size
        )

        val artwork =
            illusts[0]

        assertEquals(
            "123456",
            artwork.id
        )

        assertEquals(
            "Example artwork",
            artwork.title
        )

        assertEquals(
            3,
            artwork.page_count
        )

        assertEquals(
            1,
            artwork.x_restrict
        )

        assertEquals(
            listOf(
                "tag-a",
                "tag-b"
            ),
            artwork.tags
        )

        assertEquals(
            "42",
            artwork.author_details
                ?.user_id
        )

        assertEquals(
            "Example Artist",
            artwork.author_details
                ?.user_name
        )

        val ad =
            illusts[1]

        assertEquals(
            1,
            ad.is_ad_container
        )
    }

    @Test
    fun parsesErrorResponse() {
        val json =
            """
            {
              "error": true,
              "message": "Authentication required",
              "body": null
            }
            """.trimIndent()

        val result =
            PixivFollowingParser.parse(
                json
            )

        assertTrue(
            result.error
        )

        assertEquals(
            "Authentication required",
            result.message
        )

        assertNull(
            result.body
        )
    }

    @Test
    fun handlesNullableFields() {
        val json =
            """
            {
              "error": false,
              "message": null,
              "body": {
                "illusts": [
                  {
                    "id": "999",
                    "url": null,
                    "url_s": "https://example.com/s.jpg",
                    "url_sm": null,
                    "url_w": null,
                    "title": null,
                    "alt": null,
                    "tags": null,
                    "page_count": null,
                    "x_restrict": null,
                    "width": null,
                    "height": null,
                    "author_details": null,
                    "is_ad_container": null
                  }
                ],
                "total": null,
                "lastPage": true
              }
            }
            """.trimIndent()

        val result =
            PixivFollowingParser.parse(
                json
            )

        val body =
            requireNotNull(
                result.body
            )

        val artwork =
            requireNotNull(
                body.illusts
            ).single()

        assertEquals(
            "999",
            artwork.id
        )

        assertNull(
            artwork.title
        )

        assertNull(
            artwork.tags
        )

        assertNull(
            artwork.page_count
        )

        assertNull(
            artwork.author_details
        )

        assertEquals(
            true,
            body.lastPage
        )
    }

    @Test
    fun skipsNonObjectEntriesInIllustArray() {
        val json =
            """
            {
              "error": false,
              "message": null,
              "body": {
                "illusts": [
                  null,
                  "invalid",
                  123,
                  {
                    "id": "1",
                    "url_s": "https://example.com/s.jpg"
                  }
                ],
                "lastPage": true
              }
            }
            """.trimIndent()

        val result =
            PixivFollowingParser.parse(
                json
            )

        val illusts =
            requireNotNull(
                result.body
            )
                .illusts
                .orEmpty()

        assertEquals(
            1,
            illusts.size
        )

        assertEquals(
            "1",
            illusts.single().id
        )
    }
}