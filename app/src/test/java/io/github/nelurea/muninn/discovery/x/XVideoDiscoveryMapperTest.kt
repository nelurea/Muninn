package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.capture.web.x.XCaptureMediaPayload
import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class XVideoDiscoveryMapperTest {

    @Test
    fun `uses poster image instead of video URL for grid`() {
        val payload =
            XCapturePayload(
                sourceType = "x",
                sourceId = "123",
                canonicalUrl = "https://x.com/example/status/123",
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
                            previewUrl = "https://pbs.twimg.com/media/example?format=jpg&name=orig"
                        )
                    )
            )

        val item =
            XDiscoveryMapper.map(
                payload
            )

        assertEquals(
            "https://pbs.twimg.com/media/example?format=jpg&name=medium",
            item.previewImageUrl
        )
    }
}
