package io.github.nelurea.muninn.capturepackage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePackageParserTest {

    @Test
    fun parseAndValidatePixivMultiImagePackage() {
        val json = """
            {
              "schemaVersion": 1,
              "source": {
                "type": "pixiv",
                "id": "110078617",
                "canonicalUrl": "https://www.pixiv.net/artworks/110078617"
              },
              "capturedAt": "2026-08-09T11:44:58.320Z",
              "content": {
                "author": {
                  "id": "4792861",
                  "name": "12stairs"
                },
                "title": "velvet F",
                "caption": "",
                "tags": [
                  "オリジナル",
                  "女の子",
                  "黒髪",
                  "ポニーテール",
                  "清楚",
                  "黒髪ロング",
                  "セーラー服"
                ]
              },
              "media": [
                {
                  "index": 0,
                  "sourceUrl": "https://i.pximg.net/img-original/img/2023/07/20/15/55/27/110078617_p0.png",
                  "mimeType": "image/png",
                  "fileName": "image-0.png"
                },
                {
                  "index": 1,
                  "sourceUrl": "https://i.pximg.net/img-original/img/2023/07/20/15/55/27/110078617_p1.png",
                  "mimeType": "image/png",
                  "fileName": "image-1.png"
                },
                {
                  "index": 2,
                  "sourceUrl": "https://i.pximg.net/img-original/img/2023/07/20/15/55/27/110078617_p2.png",
                  "mimeType": "image/png",
                  "fileName": "image-2.png"
                },
                {
                  "index": 3,
                  "sourceUrl": "https://i.pximg.net/img-original/img/2023/07/20/15/55/27/110078617_p3.png",
                  "mimeType": "image/png",
                  "fileName": "image-3.png"
                }
              ]
            }
        """.trimIndent()

        val capturePackage = CapturePackageParser.parse(json)
        val errors = CapturePackageValidator.validate(capturePackage)

        assertTrue(errors.isEmpty())

        assertEquals(1, capturePackage.schemaVersion)
        assertEquals("pixiv", capturePackage.source.type)
        assertEquals("110078617", capturePackage.source.id)
        assertEquals("12stairs", capturePackage.content.author.name)
        assertEquals("velvet F", capturePackage.content.title)
        assertEquals(4, capturePackage.media.size)

        capturePackage.media.forEachIndexed { index, media ->
            assertEquals(index, media.index)
        }
    }
}