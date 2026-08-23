package io.github.nelurea.muninn.duplicate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDuplicateCleanupDeleterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deleter = AndroidDuplicateCleanupDeleter(context)

    @Test
    fun deletesPlainPathAndTreatsRetryAsMissing() {
        val file = File(context.cacheDir, "duplicate-cleanup-plain-${System.nanoTime()}").apply {
            writeText("test")
        }

        assertEquals(DuplicateCleanupDeleteResult.Deleted, deleter.delete(file.absolutePath))
        assertEquals(DuplicateCleanupDeleteResult.Missing, deleter.delete(file.absolutePath))
    }

    @Test
    fun deletesFileUriAndTreatsRetryAsMissing() {
        val file = File(context.cacheDir, "duplicate-cleanup-uri-${System.nanoTime()}").apply {
            writeText("test")
        }

        assertEquals(DuplicateCleanupDeleteResult.Deleted, deleter.delete(file.toURI().toString()))
        assertEquals(DuplicateCleanupDeleteResult.Missing, deleter.delete(file.toURI().toString()))
    }
}
