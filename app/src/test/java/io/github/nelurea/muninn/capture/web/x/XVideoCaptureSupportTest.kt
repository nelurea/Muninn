package io.github.nelurea.muninn.capture.web.x

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XVideoCaptureSupportTest {

    @Test
    fun `parses and preserves X video media`() {
        val payload =
            XCaptureParser.parseCapturePackage(
                JSONObject(
                    """
                    {
                      "source": {
                        "type": "x",
                        "id": "123456789",
                        "canonicalUrl": "https://x.com/example/status/123456789"
                      },
                      "capturedAt": "2026-08-28T00:00:00Z",
                      "content": {
                        "author": {
                          "id": "42",
                          "name": "Example",
                          "handle": "example"
                        },
                        "title": null,
                        "caption": "video",
                        "tags": []
                      },
                      "media": [
                        {
                          "index": 0,
                          "sourceUrl": "https://video.twimg.com/ext_tw_video/example/pu/vid/1920x1080/video.mp4?tag=12",
                          "previewUrl": "https://pbs.twimg.com/ext_tw_video_thumb/example/pu/img/example.jpg",
                          "mimeType": "video/mp4",
                          "fileName": "video-0.mp4"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )

        val media =
            payload.media.single()

        assertEquals(
            "video/mp4",
            media.mimeType
        )

        assertEquals(
            "https://pbs.twimg.com/ext_tw_video_thumb/example/pu/img/example.jpg",
            media.previewUrl
        )

        assertEquals(
            "https://video.twimg.com/ext_tw_video/example/pu/vid/1920x1080/video.mp4?tag=12",
            media.sourceUrl
        )
    }

    @Test
    fun `maps X video to canonical mp4 file`() {
        val payload =
            XCapturePayload(
                sourceType = "x",
                sourceId = "123456789",
                canonicalUrl = "https://x.com/example/status/123456789",
                capturedAt = "2026-08-28T00:00:00Z",
                publishedAt = null,
                authorId = "42",
                authorName = "Example",
                authorHandle = "example",
                title = null,
                caption = "video",
                tags = emptyList(),
                media =
                    listOf(
                        XCaptureMediaPayload(
                            mediaIndex = 0,
                            sourceUrl = "https://video.twimg.com/example.mp4",
                            mimeType = "video/mp4",
                            fileName = "video-0.mp4",
                            previewUrl = "https://pbs.twimg.com/media/poster.jpg"
                        )
                    )
            )

        val draft =
            XCaptureMapper.toCaptureDraft(
                payload =
                    payload,
                downloadedFiles =
                    listOf(
                        File(
                            "unused-video.mp4"
                        )
                    )
            )

        val media =
            draft.media.single()

        assertEquals(
            "video/mp4",
            media.mimeType
        )

        assertEquals(
            "x-123456789-p0.mp4",
            media.fileName
        )

        assertNotNull(
            media.sourceUrl
        )
    }
}
