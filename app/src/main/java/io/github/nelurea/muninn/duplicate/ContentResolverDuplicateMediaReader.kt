package io.github.nelurea.muninn.duplicate

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class ContentResolverDuplicateMediaReader(
    private val resolver: ContentResolver
) : DuplicateMediaReader {
    override fun open(localUri: String): InputStream? {
        val uri = Uri.parse(localUri)
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> resolver.openInputStream(uri)
            ContentResolver.SCHEME_FILE -> FileInputStream(requireNotNull(uri.path))
            null -> FileInputStream(File(localUri))
            else -> null
        }
    }
}
