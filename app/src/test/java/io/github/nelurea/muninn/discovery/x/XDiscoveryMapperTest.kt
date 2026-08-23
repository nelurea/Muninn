package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.capture.web.x.XCaptureMediaPayload
import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class XDiscoveryMapperTest {

    @Test
    fun changesOnlyNameToMediumForModernMediaUrl() {
        val sourceUrl =
            "https://pbs.twimg.com/media/example?format=webp&name=orig&tag=kept"

        val payload =
            createPayload(
                sourceUrl
            )

        val result =
            XDiscoveryMapper.map(
                payload
            )

        assertEquals(
            "https://pbs.twimg.com/media/example?format=webp&name=medium&tag=kept",
            result.previewImageUrl
        )

        assertEquals(
            sourceUrl,
            payload.media.single().sourceUrl
        )
    }

    @Test
    fun leavesNonTargetHostAndPathUnchanged() {
        val urls =
            listOf(
                "https://example.com/media/example?format=jpg&name=orig",
                "https://pbs.twimg.com/profile_images/example?format=jpg&name=orig"
            )

        urls.forEach {
                sourceUrl ->

            assertEquals(
                sourceUrl,
                XDiscoveryMapper.map(
                    createPayload(
                        sourceUrl
                    )
                ).previewImageUrl
            )
        }
    }

    @Test
    fun leavesMissingDuplicateAndInvalidQueriesUnchanged() {
        val urls =
            listOf(
                "https://pbs.twimg.com/media/example?name=orig",
                "https://pbs.twimg.com/media/example?format=jpg",
                "https://pbs.twimg.com/media/example?format=jpg&name=orig&name=small",
                "https://pbs.twimg.com/media/example?format=jpg&format=webp&name=orig",
                "https://pbs.twimg.com/media/example?format=jpg&name",
                "https://pbs.twimg.com/media/example?format=jpg&name=orig&broken",
                "https://pbs.twimg.com/media/example?format=jpg&&name=orig",
                "https://pbs.twimg.com/media/example?format=%ZZ&name=orig"
            )

        urls.forEach {
                sourceUrl ->

            assertEquals(
                sourceUrl,
                XDiscoveryMapper.map(
                    createPayload(
                        sourceUrl
                    )
                ).previewImageUrl
            )
        }
    }

    private fun createPayload(
        sourceUrl: String
    ): XCapturePayload {
        return XCapturePayload(
            sourceType = "x",
            sourceId = "123",
            canonicalUrl = "https://x.com/example/status/123",
            capturedAt = "2026-08-24T00:00:00Z",
            authorId = "42",
            authorName = "Example",
            authorHandle = "example",
            title = null,
            caption = "Caption",
            tags = emptyList(),
            media =
                listOf(
                    XCaptureMediaPayload(
                        mediaIndex = 0,
                        sourceUrl = sourceUrl,
                        mimeType = "image/jpeg",
                        fileName = "example.jpg"
                    )
                )
        )
    }
}
