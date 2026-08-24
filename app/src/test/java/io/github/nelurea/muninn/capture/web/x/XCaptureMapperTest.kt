package io.github.nelurea.muninn.capture.web.x

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class XCaptureMapperTest {
    @Test
    fun `uses source identity and media index for file name`() {
        val draft = XCaptureMapper.toCaptureDraft(
            payload(mediaIndex = 3, fileName = "image-0.JPEG", mimeType = "image/jpeg"),
            listOf(File("download.tmp"))
        )

        assertEquals("x-1890123456789012345-p3.jpeg", draft.media.single().fileName)
    }

    @Test
    fun `uses safe mime extension when supplied name has no safe extension`() {
        val draft = XCaptureMapper.toCaptureDraft(
            payload(mediaIndex = 0, fileName = "image-0", mimeType = "image/webp"),
            listOf(File("download.tmp"))
        )

        assertEquals("x-1890123456789012345-p0.webp", draft.media.single().fileName)
    }

    private fun payload(mediaIndex: Int, fileName: String, mimeType: String?) =
        XCapturePayload(
            sourceType = "x",
            sourceId = "1890123456789012345",
            canonicalUrl = "https://x.com/user/status/1890123456789012345",
            capturedAt = "2026-08-24T00:00:00Z",
            authorId = "author",
            authorName = "Author",
            authorHandle = "user",
            title = null,
            caption = "caption",
            tags = emptyList(),
            media = listOf(XCaptureMediaPayload(mediaIndex, "https://pbs.twimg.com/media/a", mimeType, fileName))
        )
}
