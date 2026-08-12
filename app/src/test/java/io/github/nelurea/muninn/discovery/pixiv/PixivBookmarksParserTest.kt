package io.github.nelurea.muninn.discovery.pixiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivBookmarksParserTest {

    @Test
    fun parsesBookmarksResponse() {
        val json =
            """
            {
              "error": false,
              "message": "",
              "body": {
                "bookmarks": [
                  {
                    "id": "123456",
                    "url": "https://example.com/regular.jpg",
                    "url_s": "https://example.com/small.jpg",
                    "url_sm": "https://example.com/medium.jpg",
                    "url_w": "https://example.com/wide.jpg",
                    "title": "Example bookmark",
                    "alt": "Example alt",
                    "tags": [
                      "tag-a",
                      "tag-b"
                    ],
                    "page_count": "13",
                    "x_restrict": "1",
                    "width": "1280",
                    "height": "1280",
                    "author_details": {
                      "user_id": "42",
                      "user_name": "Example Artist",
                      "user_account": "example"
                    }
                  }
                ],
                "total": 100,
                "lastPage": 6
              }
            }
            """.trimIndent()

        val result =
            PixivBookmarksParser.parse(
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
            6,
            body.lastPage
        )

        val bookmark =
            requireNotNull(
                body.bookmarks
            ).single()

        assertEquals(
            "123456",
            bookmark.id
        )

        assertEquals(
            "Example bookmark",
            bookmark.title
        )

        assertEquals(
            13,
            bookmark.page_count
        )

        assertEquals(
            1,
            bookmark.x_restrict
        )

        assertEquals(
            1280,
            bookmark.width
        )

        assertEquals(
            1280,
            bookmark.height
        )

        assertEquals(
            listOf(
                "tag-a",
                "tag-b"
            ),
            bookmark.tags
        )

        assertEquals(
            "42",
            bookmark.author_details
                ?.user_id
        )

        assertEquals(
            "Example Artist",
            bookmark.author_details
                ?.user_name
        )
    }

    @Test
    fun parsesNumericFieldsAsNumbersToo() {
        val json =
            """
            {
              "error": false,
              "body": {
                "bookmarks": [
                  {
                    "id": "1",
                    "url_s": "https://example.com/s.jpg",
                    "page_count": 4,
                    "x_restrict": 2,
                    "width": 800,
                    "height": 1200
                  }
                ],
                "total": "20",
                "lastPage": "2"
              }
            }
            """.trimIndent()

        val result =
            PixivBookmarksParser.parse(
                json
            )

        val body =
            requireNotNull(
                result.body
            )

        val bookmark =
            requireNotNull(
                body.bookmarks
            ).single()

        assertEquals(
            4,
            bookmark.page_count
        )

        assertEquals(
            2,
            bookmark.x_restrict
        )

        assertEquals(
            800,
            bookmark.width
        )

        assertEquals(
            1200,
            bookmark.height
        )

        assertEquals(
            20,
            body.total
        )

        assertEquals(
            2,
            body.lastPage
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
                "bookmarks": [
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
                    "author_details": null
                  }
                ],
                "total": null,
                "lastPage": null
              }
            }
            """.trimIndent()

        val result =
            PixivBookmarksParser.parse(
                json
            )

        val body =
            requireNotNull(
                result.body
            )

        val bookmark =
            requireNotNull(
                body.bookmarks
            ).single()

        assertEquals(
            "999",
            bookmark.id
        )

        assertNull(
            bookmark.title
        )

        assertNull(
            bookmark.tags
        )

        assertNull(
            bookmark.page_count
        )

        assertNull(
            bookmark.x_restrict
        )

        assertNull(
            bookmark.author_details
        )

        assertNull(
            body.total
        )

        assertNull(
            body.lastPage
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
            PixivBookmarksParser.parse(
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
    fun skipsNonObjectBookmarkEntries() {
        val json =
            """
            {
              "error": false,
              "body": {
                "bookmarks": [
                  null,
                  "invalid",
                  123,
                  {
                    "id": "1",
                    "url_s": "https://example.com/s.jpg"
                  }
                ],
                "lastPage": 1
              }
            }
            """.trimIndent()

        val result =
            PixivBookmarksParser.parse(
                json
            )

        val bookmarks =
            requireNotNull(
                result.body
            )
                .bookmarks
                .orEmpty()

        assertEquals(
            1,
            bookmarks.size
        )

        assertEquals(
            "1",
            bookmarks.single().id
        )
    }
}