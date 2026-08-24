package io.github.nelurea.muninn.media.move

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MediaMoveFileOperations {
    suspend fun isAtDestination(sourceUri: String, destinationRootUri: String?): Boolean
    suspend fun createDestination(mediaId: Long, fileName: String, mimeType: String, destinationRootUri: String?): String
    suspend fun getFileName(uri: String): String
    suspend fun copyAndVerify(sourceUri: String, destinationUri: String): Long
    suspend fun cleanupDestination(destinationUri: String): Boolean
    suspend fun delete(uri: String, mediaId: Long): Boolean
}

class AndroidMediaMoveFileOperations(context: Context) : MediaMoveFileOperations {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun isAtDestination(sourceUri: String, destinationRootUri: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (destinationRootUri == null) {
                val source = fileFor(sourceUri) ?: return@withContext false
                val normalRoot = File(appContext.filesDir, "captured_media")
                val moveRoot = File(appContext.filesDir, "captured_media_moved")
                return@withContext isWithin(source, normalRoot) || isWithin(source, moveRoot)
            }

            val source = Uri.parse(sourceUri)
            val tree = Uri.parse(destinationRootUri)
            if (source.scheme != "content" || tree.scheme != "content" || source.authority != tree.authority) {
                return@withContext false
            }
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                ?: return@withContext false
            val documentId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull()
                ?: return@withContext false
            DocumentIdContainment.contains(treeId, documentId)
        }

    override suspend fun createDestination(
        mediaId: Long,
        fileName: String,
        mimeType: String,
        destinationRootUri: String?
    ): String = withContext(Dispatchers.IO) {
        if (destinationRootUri == null) {
            val directory = File(appContext.filesDir, "captured_media_moved/$mediaId")
            check(directory.exists() || directory.mkdirs()) { "Could not create internal destination" }
            val destination = File(directory, fileName)
            check(!destination.exists() || destination.length() == 0L) { "Destination already exists" }
            return@withContext Uri.fromFile(destination).toString()
        }

        val treeUri = Uri.parse(destinationRootUri)
        require(treeUri.scheme == "content") { "Destination root must be a content URI" }
        val root = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        DocumentsContract.createDocument(
            resolver,
            root,
            mimeType.ifBlank { "application/octet-stream" },
            fileName
        )?.toString() ?: error("Could not create destination document")
    }

    override suspend fun getFileName(uri: String): String = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            "content" -> queryFileName(parsed)
            "file" -> parsed.path?.let(::File)?.name
            null -> File(uri).name
            else -> null
        } ?: error("Could not determine destination file name")
    }

    override suspend fun copyAndVerify(sourceUri: String, destinationUri: String): Long =
        withContext(Dispatchers.IO) {
            val copied = openInput(sourceUri).use { input ->
                openOutput(destinationUri).use { output -> input.copyTo(output) }
            }
            val verified = openInput(destinationUri).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
                total
            }
            check(copied == verified) { "Destination length differs after copy" }
            copied
        }

    override suspend fun cleanupDestination(destinationUri: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(destinationUri)
        try {
            when (uri.scheme) {
                "content" -> if (DocumentsContract.isDocumentUri(appContext, uri)) {
                    DocumentsContract.deleteDocument(resolver, uri)
                } else {
                    resolver.delete(uri, null, null) > 0
                }
                "file", null -> {
                    val file = File(uri.path ?: destinationUri)
                    !file.exists() || file.delete()
                }
                else -> false
            }
        } catch (_: FileNotFoundException) {
            true
        }
    }

    override suspend fun delete(uri: String, mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            "content" -> {
                val legacyDirectory = runCatching { findLegacyDirectory(parsed, mediaId) }.getOrNull()
                val sourceWasInLegacyDirectory = legacyDirectory?.let { directory ->
                    runCatching {
                        val documentId = DocumentsContract.getDocumentId(parsed)
                        hasChild(directory, parsed, documentId)
                    }.getOrDefault(false)
                } == true
                try {
                    val deleted = if (DocumentsContract.isDocumentUri(appContext, parsed)) {
                        DocumentsContract.deleteDocument(resolver, parsed)
                    } else {
                        resolver.delete(parsed, null, null) > 0
                    }
                    if (deleted && sourceWasInLegacyDirectory) {
                        legacyDirectory?.let(::deleteDirectoryIfEmpty)
                    }
                    deleted
                } catch (_: FileNotFoundException) {
                    // Deletion is idempotent: the old source may have been removed
                    // before the journal could be marked completed.
                    if (legacyDirectory != null) {
                        deleteDirectoryIfEmpty(legacyDirectory)
                    }
                    true
                }
            }
            "file", null -> {
                val file = File(parsed.path ?: uri)
                !file.exists() || file.delete()
            }
            else -> false
        }
    }

    private fun fileFor(rawUri: String): File? {
        val uri = Uri.parse(rawUri)
        return when (uri.scheme) {
            "file" -> uri.path?.let(::File)
            null -> File(rawUri)
            else -> null
        }
    }

    private fun isWithin(file: File, root: File): Boolean {
        val filePath = runCatching { file.canonicalFile.toPath() }.getOrNull() ?: return false
        val rootPath = runCatching { root.canonicalFile.toPath() }.getOrNull() ?: return false
        return filePath.startsWith(rootPath)
    }

    private fun findChild(parent: Uri, treeUri: Uri, displayName: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parent)
        )
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                }
            }
        }
        return null
    }

    private fun findLegacyDirectory(documentUri: Uri, mediaId: Long): Uri? {
        if (!DocumentsContract.isDocumentUri(appContext, documentUri)) return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(documentUri) }.getOrNull()
            ?: return null
        val root = DocumentsContract.buildDocumentUriUsingTree(documentUri, treeId)
        return findChild(root, documentUri, ".muninn-move-$mediaId")
    }

    private fun hasChild(parent: Uri, treeUri: Uri, documentId: String): Boolean =
        queryChildDocumentIds(parent, treeUri)?.any { it == documentId } == true

    private fun deleteDirectoryIfEmpty(directory: Uri) {
        runCatching {
            if (queryChildDocumentIds(directory, directory)?.isEmpty() == true) {
                DocumentsContract.deleteDocument(resolver, directory)
            }
        }
    }

    private fun queryChildDocumentIds(parent: Uri, treeUri: Uri): List<String>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parent)
        )
        return resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(idColumn))
            }
        }
    }

    private fun openInput(rawUri: String): InputStream {
        val uri = Uri.parse(rawUri)
        return when (uri.scheme) {
            "content" -> resolver.openInputStream(uri)
            "file" -> uri.path?.let(::File)?.inputStream()
            null -> File(rawUri).inputStream()
            else -> null
        } ?: error("Could not open source $rawUri")
    }

    private fun queryFileName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) cursor.getString(column) else null
        } ?: error("Could not determine destination file name")

    private fun openOutput(rawUri: String): OutputStream {
        val uri = Uri.parse(rawUri)
        return when (uri.scheme) {
            "content" -> resolver.openOutputStream(uri, "wt")
            "file" -> uri.path?.let(::File)?.outputStream()
            null -> File(rawUri).outputStream()
            else -> null
        } ?: error("Could not open destination $rawUri")
    }
}

internal object DocumentIdContainment {
    fun contains(treeDocumentId: String, documentId: String): Boolean =
        documentId == treeDocumentId ||
            documentId.startsWith(treeDocumentId.withTrailingSeparator())

    private fun String.withTrailingSeparator(): String =
        if (endsWith('/') || endsWith(':')) this else "$this/"
}
