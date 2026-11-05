package io.github.nelurea.muninn.capture.storage

import java.io.File
import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InternalMediaFileCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    private val batchName = "12345678-1234-1234-1234-123456789abc"

    @Test fun deletesOnlyTheRequestedFile() {
        val filesDir = temporary.newFolder("files")
        val batch = File(filesDir, "captured_media/$batchName").apply { mkdirs() }
        val target = File(batch, "discard.jpg").apply { writeText("discard") }
        val keep = File(batch, "keep.jpg").apply { writeText("keep") }

        val androidStyleUri = URI(
            "file", "", target.toURI().path, null
        ).toString()
        InternalMediaFileCleanup(filesDir).delete(androidStyleUri)

        assertFalse(target.exists())
        assertTrue(keep.isFile)
        assertTrue(batch.isDirectory)
    }

    @Test fun removesOnlyAnEmptyBatchDirectory() {
        val filesDir = temporary.newFolder("files")
        val root = File(filesDir, "captured_media")
        val batch = File(root, batchName).apply { mkdirs() }
        val otherBatch = File(root, "12345678-1234-1234-1234-123456789abd").apply { mkdirs() }
        val target = File(batch, "one.jpg").apply { writeText("one") }
        val keep = File(otherBatch, "two.jpg").apply { writeText("two") }

        InternalMediaFileCleanup(filesDir).delete(target.toURI().toString())

        assertFalse(batch.exists())
        assertTrue(root.isDirectory)
        assertTrue(keep.isFile)
    }

    @Test fun refusesFilesOutsideOwnedBatch() {
        val filesDir = temporary.newFolder("files")
        val outside = temporary.newFile("outside.jpg").apply { writeText("keep") }
        val nonUuid = File(filesDir, "captured_media/unrecognized/image.jpg").apply {
            parentFile!!.mkdirs()
            writeText("keep")
        }
        val nested = File(filesDir, "captured_media/$batchName/nested/image.jpg").apply {
            parentFile!!.mkdirs()
            writeText("keep")
        }
        val cleanup = InternalMediaFileCleanup(filesDir)

        listOf(outside, nonUuid, nested).forEach { cleanup.delete(it.toURI().toString()) }

        assertTrue(outside.isFile)
        assertTrue(nonUuid.isFile)
        assertTrue(nested.isFile)
    }

    @Test fun refusesNonFileAndMalformedUris() {
        val filesDir = temporary.newFolder("files")
        val batch = File(filesDir, "captured_media/$batchName").apply { mkdirs() }
        val image = File(batch, "keep.jpg").apply { writeText("keep") }
        val cleanup = InternalMediaFileCleanup(filesDir)

        cleanup.delete("content://example.invalid/$batchName/keep.jpg")
        cleanup.delete(image.absolutePath)
        cleanup.delete("file:///invalid%ZZ")
        cleanup.delete(image.toURI().toString() + "?delete=1")

        assertTrue(image.isFile)
    }

    @Test fun rejectsTraversalFileNames() {
        assertTrue(InternalMediaFileCleanup.isSafeFileName("photo.jpg"))
        for (name in listOf("", ".", "..", "../other.jpg", "sub/file.jpg", "sub\\file.jpg")) {
            assertFalse(name, InternalMediaFileCleanup.isSafeFileName(name))
        }
    }
}
