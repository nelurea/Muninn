package io.github.nelurea.muninn.capture.web.pixiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivCaptureParserTest {

    @Test
    fun parse_validCapture_returnsPayload() {
        val rawMessage =
            """
            {
              "type": "PIXIV_CAPTURE_PROBE_RESULT",
              "ok": true,
              "capturePackage": {
                "schemaVersion": 1,
                "source": {
                  "type": "pixiv",
                  "id": "123456",
                  "canonicalUrl": "https://www.pixiv.net/artworks/123456"
                },
                "capturedAt": "2026-08-11T12:00:00.000Z",
                "publishedAt": "2026-08-10T03:30:00.000Z",
                "content": {
                  "author": {
                    "id": "42",
                    "name": "Author"
                  },
                  "title": "Title",
                  "caption": "Caption",
                  "tags": [
                    "tag-a",
                    "tag-b"
                  ]
                },
                "media": [
                  {
                    "index": 0,
                    "sourceUrl": "https://i.pximg.net/example_p0.jpg",
                    "mimeType": "image/jpeg",
                    "fileName": "image-0.jpg"
                  }
                ]
              }
            }
            """.trimIndent()

        val result =
            PixivCaptureParser.parse(
                rawMessage
            )

        assertTrue(
            result is PixivCaptureParseResult.Success
        )

        val payload =
            (result as PixivCaptureParseResult.Success)
                .payload

        assertEquals(
            "pixiv",
            payload.sourceType
        )

        assertEquals(
            "123456",
            payload.sourceId
        )

        assertEquals(
            "https://www.pixiv.net/artworks/123456",
            payload.canonicalUrl
        )

        assertEquals(
            "2026-08-11T12:00:00.000Z",
            payload.capturedAt
        )

        assertEquals(
            "2026-08-10T03:30:00.000Z",
            payload.publishedAt
        )

        assertEquals(
            "42",
            payload.authorId
        )

        assertEquals(
            "Author",
            payload.authorName
        )

        assertEquals(
            "Title",
            payload.title
        )

        assertEquals(
            "Caption",
            payload.caption
        )

        assertEquals(
            listOf(
                "tag-a",
                "tag-b"
            ),
            payload.tags
        )

        assertEquals(
            1,
            payload.media.size
        )

        assertEquals(
            "image/jpeg",
            payload.media.single().mimeType
        )
    }

    @Test
    fun parse_nullableFields_preservesNulls() {
        val rawMessage =
            """
            {
              "type": "PIXIV_CAPTURE_PROBE_RESULT",
              "ok": true,
              "capturePackage": {
                "schemaVersion": 1,
                "source": {
                  "type": "pixiv",
                  "id": "123456",
                  "canonicalUrl": "https://www.pixiv.net/artworks/123456"
                },
                "capturedAt": "2026-08-11T12:00:00.000Z",
                "content": {
                  "author": {
                    "id": null,
                    "name": null
                  },
                  "title": null,
                  "caption": null,
                  "tags": []
                },
                "media": [
                  {
                    "index": 0,
                    "sourceUrl": "https://i.pximg.net/example_p0",
                    "mimeType": null,
                    "fileName": "image-0"
                  }
                ]
              }
            }
            """.trimIndent()

        val result =
            PixivCaptureParser.parse(
                rawMessage
            )

        assertTrue(
            result is PixivCaptureParseResult.Success
        )

        val payload =
            (result as PixivCaptureParseResult.Success)
                .payload

        assertNull(
            payload.publishedAt
        )

        assertNull(
            payload.authorId
        )

        assertNull(
            payload.authorName
        )

        assertNull(
            payload.title
        )

        assertNull(
            payload.caption
        )

        assertNull(
            payload.media.single().mimeType
        )
    }

    @Test
    fun parse_literalNa_preservesStringValue() {
        val rawMessage =
            """
            {
              "type": "PIXIV_CAPTURE_PROBE_RESULT",
              "ok": true,
              "capturePackage": {
                "schemaVersion": 1,
                "source": {
                  "type": "pixiv",
                  "id": "123456",
                  "canonicalUrl": "https://www.pixiv.net/artworks/123456"
                },
                "capturedAt": "2026-08-11T12:00:00.000Z",
                "content": {
                  "author": {
                    "id": "99",
                    "name": "N/A"
                  },
                  "title": "N/A",
                  "caption": "N/A",
                  "tags": []
                },
                "media": [
                  {
                    "index": 0,
                    "sourceUrl": "https://i.pximg.net/example_p0.jpg",
                    "mimeType": "image/jpeg",
                    "fileName": "image-0.jpg"
                  }
                ]
              }
            }
            """.trimIndent()

        val result =
            PixivCaptureParser.parse(
                rawMessage
            )

        assertTrue(
            result is PixivCaptureParseResult.Success
        )

        val payload =
            (result as PixivCaptureParseResult.Success)
                .payload

        assertEquals(
            "N/A",
            payload.authorName
        )

        assertEquals(
            "N/A",
            payload.title
        )

        assertEquals(
            "N/A",
            payload.caption
        )
    }
}