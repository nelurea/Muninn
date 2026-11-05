package io.github.nelurea.muninn.capture.storage

import java.io.File
import java.net.URI

internal class InternalMediaFileCleanup(private val filesDir: File) {
    fun delete(uriText: String) {
        val file = runCatching {
            val uri = URI(uriText)
            if (uri.scheme != "file" || uri.authority != null ||
                uri.query != null || uri.fragment != null) return
            File(uri)
        }.getOrNull() ?: return
        val batch = file.parentFile ?: return
        if (!BATCH_NAME.matches(batch.name)) return

        val withinOwnedBatch = runCatching {
            val root = File(filesDir, "captured_media").canonicalFile
            val resolvedBatch = batch.canonicalFile
            val resolvedFile = file.canonicalFile
            resolvedBatch.parentFile == root && resolvedBatch.name == batch.name &&
                resolvedFile.parentFile == resolvedBatch && resolvedFile.name == file.name &&
                file.isFile
        }.getOrDefault(false)
        if (!withinOwnedBatch) return

        if (file.delete() && batch.listFiles()?.isEmpty() == true) batch.delete()
    }

    companion object {
        private val BATCH_NAME = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        )

        fun isSafeFileName(name: String): Boolean =
            name.isNotEmpty() && name != "." && name != ".." &&
                '/' !in name && '\\' !in name && '\u0000' !in name
    }
}
