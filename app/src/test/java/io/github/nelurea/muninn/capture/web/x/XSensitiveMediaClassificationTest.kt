package io.github.nelurea.muninn.capture.web.x

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XSensitiveMediaClassificationTest {

    @Test
    fun `parser reads canonical sensitive flag`() {
        val payload =
            XCaptureParser.parseCapturePackage(
                JSONObject(
                    """
                    {
                      "source": {
                        "type": "x",
                        "id": "123",
                        "canonicalUrl": "https://x.com/example/status/123"
                      },
                      "capturedAt": "2026-09-03T00:00:00Z",
                      "content": {
                        "author": {
                          "id": "42",
                          "name": "Example",
                          "handle": "example"
                        },
                        "title": null,
                        "caption": "caption",
                        "tags": [],
                        "isSensitive": true
                      },
                      "media": [
                        {
                          "index": 0,
                          "sourceUrl": "https://pbs.twimg.com/media/example.jpg",
                          "mimeType": "image/jpeg",
                          "fileName": "image-0.jpg"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )

        assertTrue(
            payload.isSensitive
        )
    }

    @Test
    fun `sensitive X content adds Sensitive tag`() {
        val draft =
            XCaptureMapper.toCaptureDraft(
                payload =
                    payload(
                        isSensitive =
                            true
                    ),
                downloadedFiles =
                    listOf(
                        File(
                            "unused.jpg"
                        )
                    )
            )

        assertEquals(
            listOf(
                "original",
                "Sensitive"
            ),
            draft.tags
        )
    }

    @Test
    fun `non sensitive X content does not add Sensitive tag`() {
        val draft =
            XCaptureMapper.toCaptureDraft(
                payload =
                    payload(
                        isSensitive =
                            false
                    ),
                downloadedFiles =
                    listOf(
                        File(
                            "unused.jpg"
                        )
                    )
            )

        assertEquals(
            listOf(
                "original"
            ),
            draft.tags
        )

        assertFalse(
            draft.tags.contains(
                "Sensitive"
            )
        )
    }

    @Test
    fun `classification does not duplicate existing Sensitive tag`() {
        val draft =
            XCaptureMapper.toCaptureDraft(
                payload =
                    payload(
                        tags =
                            listOf(
                                "original",
                                "Sensitive"
                            ),
                        isSensitive =
                            true
                    ),
                downloadedFiles =
                    listOf(
                        File(
                            "unused.jpg"
                        )
                    )
            )

        assertEquals(
            listOf(
                "original",
                "Sensitive"
            ),
            draft.tags
        )
    }

    private fun payload(
        tags: List<String> =
            listOf(
                "original"
            ),
        isSensitive: Boolean =
            false
    ): XCapturePayload =
        XCapturePayload(
            sourceType =
                "x",
            sourceId =
                "123",
            canonicalUrl =
                "https://x.com/example/status/123",
            capturedAt =
                "2026-09-03T00:00:00Z",
            publishedAt =
                null,
            authorId =
                "42",
            authorName =
                "Example",
            authorHandle =
                "example",
            title =
                null,
            caption =
                "caption",
            tags =
                tags,
            isSensitive =
                isSensitive,
            media =
                listOf(
                    XCaptureMediaPayload(
                        mediaIndex =
                            0,
                        sourceUrl =
                            "https://pbs.twimg.com/media/example.jpg",
                        mimeType =
                            "image/jpeg",
                        fileName =
                            "image-0.jpg"
                    )
                )
        )
}