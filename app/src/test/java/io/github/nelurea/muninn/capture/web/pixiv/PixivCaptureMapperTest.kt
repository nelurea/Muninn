package io.github.nelurea.muninn.capture.web.pixiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PixivCaptureMapperTest {

    @Test
    fun toCaptureDraft_mapsPayloadAndFiles() {
        val sourceFile =
            File(
                "build/tmp/test-image.jpg"
            )

        val payload =
            PixivCapturePayload(
                sourceType =
                    "pixiv",

                sourceId =
                    "123456",

                canonicalUrl =
                    "https://www.pixiv.net/artworks/123456",

                capturedAt =
                    "2026-08-11T12:00:00.000Z",

                authorId =
                    "42",

                authorName =
                    "Author",

                title =
                    "Title",

                caption =
                    "Caption",

                tags =
                    listOf(
                        "tag-a",
                        "tag-b"
                    ),

                media =
                    listOf(
                        PixivCaptureMediaPayload(
                            mediaIndex =
                                0,

                            sourceUrl =
                                "https://i.pximg.net/example_p0.jpg",

                            mimeType =
                                "image/jpeg",

                            fileName =
                                "image-0.jpg"
                        )
                    )
            )

        val draft =
            PixivCaptureMapper.toCaptureDraft(
                payload =
                    payload,
                downloadedFiles =
                    listOf(
                        sourceFile
                    )
            )

        assertEquals(
            "pixiv",
            draft.sourceType
        )

        assertEquals(
            "123456",
            draft.sourceId
        )

        assertEquals(
            "42",
            draft.authorId
        )

        assertEquals(
            "Author",
            draft.authorName
        )

        assertEquals(
            "Title",
            draft.title
        )

        assertEquals(
            "Caption",
            draft.caption
        )

        assertEquals(
            listOf(
                "tag-a",
                "tag-b"
            ),
            draft.tags
        )

        assertEquals(
            1,
            draft.media.size
        )

        assertEquals(
            sourceFile,
            draft.media.single().sourceFile
        )

        assertEquals(
            "image/jpeg",
            draft.media.single().mimeType
        )
    }

    @Test
    fun toCaptureDraft_normalizesNullableFieldsAtBoundary() {
        val sourceFile =
            File(
                "build/tmp/test-image"
            )

        val payload =
            PixivCapturePayload(
                sourceType =
                    "pixiv",

                sourceId =
                    "123456",

                canonicalUrl =
                    "https://www.pixiv.net/artworks/123456",

                capturedAt =
                    "2026-08-11T12:00:00.000Z",

                authorId =
                    null,

                authorName =
                    null,

                title =
                    null,

                caption =
                    null,

                tags =
                    emptyList(),

                media =
                    listOf(
                        PixivCaptureMediaPayload(
                            mediaIndex =
                                0,

                            sourceUrl =
                                "https://i.pximg.net/example_p0",

                            mimeType =
                                null,

                            fileName =
                                "image-0"
                        )
                    )
            )

        val draft =
            PixivCaptureMapper.toCaptureDraft(
                payload =
                    payload,
                downloadedFiles =
                    listOf(
                        sourceFile
                    )
            )

        assertEquals(
            "",
            draft.authorId
        )

        assertEquals(
            "",
            draft.authorName
        )

        assertNull(
            draft.title
        )

        assertEquals(
            "",
            draft.caption
        )

        assertEquals(
            "application/octet-stream",
            draft.media.single().mimeType
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun toCaptureDraft_rejectsMediaCountMismatch() {
        val payload =
            PixivCapturePayload(
                sourceType =
                    "pixiv",

                sourceId =
                    "123456",

                canonicalUrl =
                    "https://www.pixiv.net/artworks/123456",

                capturedAt =
                    "2026-08-11T12:00:00.000Z",

                authorId =
                    "42",

                authorName =
                    "Author",

                title =
                    "Title",

                caption =
                    "Caption",

                tags =
                    emptyList(),

                media =
                    listOf(
                        PixivCaptureMediaPayload(
                            mediaIndex =
                                0,

                            sourceUrl =
                                "https://i.pximg.net/example_p0.jpg",

                            mimeType =
                                "image/jpeg",

                            fileName =
                                "image-0.jpg"
                        )
                    )
            )

        PixivCaptureMapper.toCaptureDraft(
            payload =
                payload,
            downloadedFiles =
                emptyList()
        )
    }
}