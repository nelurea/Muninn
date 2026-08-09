package io.github.nelurea.muninn.capturepackage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CapturePackageReaderTest {

    @Test
    fun readValidMultiImagePackage() {
        val directory = Files.createTempDirectory("capture-package-test").toFile()

        try {
            File(directory, "manifest.json").writeText(manifestJson)

            repeat(4) { index ->
                File(directory, "image-$index.png")
                    .writeBytes(byteArrayOf(1, 2, 3))
            }

            val result = CapturePackageReader.read(directory)

            assertTrue(result is CapturePackageReadResult.Success)

            val success = result as CapturePackageReadResult.Success

            assertEquals(
                "110078617",
                success.loadedPackage.capturePackage.source.id
            )
            assertEquals(
                "velvet F",
                success.loadedPackage.capturePackage.content.title
            )
            assertEquals(
                4,
                success.loadedPackage.mediaFiles.size
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectPackageWhenMediaFileIsMissing() {
        val directory = Files.createTempDirectory("capture-package-test").toFile()

        try {
            File(directory, "manifest.json").writeText(manifestJson)

            // image-3.png is intentionally missing.
            repeat(3) { index ->
                File(directory, "image-$index.png")
                    .writeBytes(byteArrayOf(1, 2, 3))
            }

            val result = CapturePackageReader.read(directory)

            assertTrue(result is CapturePackageReadResult.Failure)

            val failure = result as CapturePackageReadResult.Failure

            assertTrue(
                failure.errors.any {
                    it.contains("image-3.png")
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private val manifestJson = """
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
}