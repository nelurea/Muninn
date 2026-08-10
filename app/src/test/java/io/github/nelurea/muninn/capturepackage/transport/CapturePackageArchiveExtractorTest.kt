package io.github.nelurea.muninn.capturepackage.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CapturePackageArchiveExtractorTest {

    @get:Rule
    val temporaryFolder =
        TemporaryFolder()

    @Test
    fun extract_validArchive_extractsAllFiles() {
        val archive =
            createZip(
                "valid.zip",
                listOf(
                    "manifest.json" to
                            """{"schemaVersion":1}""".toByteArray(),
                    "0.jpg" to
                            byteArrayOf(1, 2, 3, 4)
                )
            )

        val extractionRoot =
            temporaryFolder.newFolder(
                "valid-extraction"
            )

        val extractor =
            CapturePackageArchiveExtractor(
                cacheDirectory = extractionRoot
            )

        val result =
            extractor.extract(
                archive
            )

        assertTrue(result.isDirectory)

        val manifest =
            File(
                result,
                "manifest.json"
            )

        val media =
            File(
                result,
                "0.jpg"
            )

        assertTrue(manifest.isFile)
        assertTrue(media.isFile)

        assertEquals(
            """{"schemaVersion":1}""",
            manifest.readText()
        )

        assertEquals(
            listOf<Byte>(1, 2, 3, 4),
            media.readBytes().toList()
        )
    }

    @Test
    fun extract_emptyArchive_isRejectedAndCleanedUp() {
        val archive =
            createZip(
                "empty.zip",
                emptyList()
            )

        val extractionRoot =
            temporaryFolder.newFolder(
                "empty-extraction"
            )

        val extractor =
            CapturePackageArchiveExtractor(
                cacheDirectory = extractionRoot
            )

        assertFailureContaining(
            "CapturePackage archive is empty"
        ) {
            extractor.extract(
                archive
            )
        }

        assertDirectoryEmpty(
            extractionRoot
        )
    }

    @Test
    fun extract_zipSlipEntry_isRejectedAndCleanedUp() {
        val archive =
            createZip(
                "zip-slip.zip",
                listOf(
                    "../evil.txt" to
                            "evil".toByteArray()
                )
            )

        val extractionRoot =
            temporaryFolder.newFolder(
                "zip-slip-extraction"
            )

        val extractor =
            CapturePackageArchiveExtractor(
                cacheDirectory = extractionRoot
            )

        assertFailureContaining(
            "Unsafe ZIP entry path"
        ) {
            extractor.extract(
                archive
            )
        }

        assertFalse(
            File(
                extractionRoot,
                "evil.txt"
            ).exists()
        )

        assertDirectoryEmpty(
            extractionRoot
        )
    }

    @Test
    fun extract_tooManyEntries_isRejectedAndCleanedUp() {
        val archive =
            createZip(
                "too-many-entries.zip",
                listOf(
                    "0.txt" to
                            "0".toByteArray(),
                    "1.txt" to
                            "1".toByteArray(),
                    "2.txt" to
                            "2".toByteArray()
                )
            )

        val extractionRoot =
            temporaryFolder.newFolder(
                "entry-limit-extraction"
            )

        val extractor =
            CapturePackageArchiveExtractor(
                cacheDirectory =
                    extractionRoot,
                maxEntryCount = 2
            )

        assertFailureContaining(
            "CapturePackage contains too many ZIP entries"
        ) {
            extractor.extract(
                archive
            )
        }

        assertDirectoryEmpty(
            extractionRoot
        )
    }

    @Test
    fun extract_extractedSizeExceedsLimit_isRejectedAndCleanedUp() {
        val archive =
            createZip(
                "too-large.zip",
                listOf(
                    "0.bin" to
                            byteArrayOf(
                                1,
                                2,
                                3,
                                4,
                                5,
                                6
                            )
                )
            )

        val extractionRoot =
            temporaryFolder.newFolder(
                "size-limit-extraction"
            )

        val extractor =
            CapturePackageArchiveExtractor(
                cacheDirectory =
                    extractionRoot,
                maxExtractedBytes = 5
            )

        assertFailureContaining(
            "CapturePackage is too large"
        ) {
            extractor.extract(
                archive
            )
        }

        assertDirectoryEmpty(
            extractionRoot
        )
    }

    private fun createZip(
        fileName: String,
        entries: List<Pair<String, ByteArray>>
    ): File {
        val archive =
            temporaryFolder.newFile(
                fileName
            )

        ZipOutputStream(
            archive.outputStream()
        ).use { zipOutput ->
            entries.forEach { (name, bytes) ->
                zipOutput.putNextEntry(
                    ZipEntry(name)
                )

                zipOutput.write(bytes)

                zipOutput.closeEntry()
            }
        }

        return archive
    }

    private fun assertFailureContaining(
        expectedMessage: String,
        block: () -> Unit
    ) {
        try {
            block()

            fail(
                "Expected exception containing: $expectedMessage"
            )
        } catch (exception: IllegalArgumentException) {
            assertTrue(
                "Expected <$expectedMessage>, " +
                        "but was <${exception.message}>",
                exception.message
                    ?.contains(expectedMessage)
                        == true
            )
        }
    }

    private fun assertDirectoryEmpty(
        directory: File
    ) {
        assertTrue(
            directory
                .listFiles()
                .orEmpty()
                .isEmpty()
        )
    }
}
