package io.github.nelurea.muninn.capture.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafMediaStorage(
    context: Context,
    treeUriString: String
) : MediaStorage {

    private val contentResolver =
        context.applicationContext
            .contentResolver

    private val treeUri =
        Uri.parse(
            treeUriString
        )

    override suspend fun store(
        media: List<CaptureMediaDraft>
    ): MediaStorageResult =
        withContext(
            Dispatchers.IO
        ) {
            val createdUris =
                mutableListOf<Uri>()

            try {
                val rootDocumentUri =
                    DocumentsContract
                        .buildDocumentUriUsingTree(
                            treeUri,
                            DocumentsContract
                                .getTreeDocumentId(
                                    treeUri
                                )
                        )

                val localUris =
                    media.map {
                            item ->

                        val mimeType =
                            item.mimeType
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?: "application/octet-stream"

                        val destinationUri =
                            DocumentsContract
                                .createDocument(
                                    contentResolver,
                                    rootDocumentUri,
                                    mimeType,
                                    item.fileName
                                )
                                ?: throw IllegalStateException(
                                    "Could not create ${item.fileName}"
                                )

                        createdUris +=
                            destinationUri

                        contentResolver
                            .openOutputStream(
                                destinationUri,
                                "w"
                            )
                            ?.use {
                                    output ->

                                item.sourceFile
                                    .inputStream()
                                    .use {
                                            input ->

                                        input.copyTo(
                                            output
                                        )
                                    }
                            }
                            ?: throw IllegalStateException(
                                "Could not open ${item.fileName} for writing"
                            )

                        destinationUri
                            .toString()
                    }

                MediaStorageResult.Success(
                    localUris =
                        localUris
                )
            } catch (
                exception: Exception
            ) {
                createdUris.forEach {
                        uri ->

                    runCatching {
                        DocumentsContract
                            .deleteDocument(
                                contentResolver,
                                uri
                            )
                    }
                }

                MediaStorageResult.Failure(
                    exception.message
                        ?: "Could not write media to selected storage"
                )
            }
        }

    override suspend fun delete(
        localUris: List<String>
    ) {
        withContext(
            Dispatchers.IO
        ) {
            localUris.forEach {
                    rawUri ->

                val uri =
                    Uri.parse(
                        rawUri
                    )

                runCatching {
                    DocumentsContract
                        .deleteDocument(
                            contentResolver,
                            uri
                        )
                }
            }
        }
    }
}