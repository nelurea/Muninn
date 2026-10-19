package io.github.nelurea.muninn.data.context

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Stores Context in its own child folder; never writes to or moves saved media. */
class SafContextDocumentStore(context: Context, private val treeUri: Uri) : ContextDocumentStore {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private fun requireGrant() {
        val persisted = resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!persisted) throw SecurityException("Context folder read/write permission is not persisted")
    }

    private fun root(): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
    )

    private data class SafEntry(val uri: Uri, val name: String, val mime: String)

    private fun children(parent: Uri): List<SafEntry> {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getDocumentId(parent)
        )
        val entries = mutableListOf<SafEntry>()
        resolver.query(childUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val name = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                entries += SafEntry(
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(id)),
                    cursor.getString(name), cursor.getString(mime)
                )
            }
        } ?: throw IOException("Cannot enumerate SAF Context directory")
        return entries
    }

    private fun directory(create: Boolean): Uri? {
        requireGrant()
        val matches = children(root()).filter { it.name == CONTEXT_FOLDER }
        if (matches.size > 1) throw IOException("Ambiguous SAF Context directory")
        if (matches.isNotEmpty()) {
            if (matches.single().mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                throw IOException("Context directory name is occupied by a file")
            }
            return matches.single().uri
        }
        if (!create) return null
        return DocumentsContract.createDocument(
            resolver, root(), DocumentsContract.Document.MIME_TYPE_DIR, CONTEXT_FOLDER
        ) ?: throw IOException("Could not create SAF Context directory")
    }

    override fun list(): List<ContextDocument> {
        val dir = directory(create = false) ?: return emptyList()
        return children(dir).filter { it.mime != DocumentsContract.Document.MIME_TYPE_DIR }
            .map { ContextDocument(it.uri.toString(), it.name) }
    }

    override fun create(displayName: String, mimeType: String): ContextDocument {
        val dir = directory(create = true) ?: throw IOException("Missing Context directory")
        val uri = DocumentsContract.createDocument(resolver, dir, mimeType, displayName)
            ?: throw IOException("Could not create SAF Context document")
        val name = resolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) throw IOException("SAF document not found after creation")
            cursor.getString(0)
        } ?: throw IOException("Could not inspect SAF Context document")
        return ContextDocument(uri.toString(), name)
    }

    override fun write(document: ContextDocument, bytes: ByteArray) {
        if (bytes.size > MAX_DOCUMENT_BYTES) throw IOException("Context document exceeds size limit")
        resolver.openOutputStream(Uri.parse(document.uri), "w")?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: throw IOException("Cannot open SAF Context document for writing")
    }

    override fun read(document: ContextDocument): ByteArray {
        val output = ByteArrayOutputStream()
        resolver.openInputStream(Uri.parse(document.uri))?.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_DOCUMENT_BYTES) {
                    throw IOException("SAF Context document exceeds size limit")
                }
                output.write(buffer, 0, count)
            }
        } ?: throw IOException("Cannot open SAF Context document for reading")
        return output.toByteArray()
    }

    companion object {
        const val CONTEXT_FOLDER = "Muninn-Context"
        private const val MAX_DOCUMENT_BYTES = 64 * 1024 * 1024

        /** Explicit background-IO boundary for callers of the synchronous journal. */
        suspend fun <T> onIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }
    }
}
