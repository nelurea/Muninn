package io.github.nelurea.muninn.capture.storage

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InternalMediaStorage(
    context: Context
) : MediaStorage {

    private val filesDir =
        context.applicationContext.filesDir

    override suspend fun store(
        media: List<CaptureMediaDraft>
    ): MediaStorageResult =
        withContext(
            Dispatchers.IO
        ) {
            val destinationDirectory =
                File(
                    filesDir,
                    "captured_media/${UUID.randomUUID()}"
                )

            if (
                !destinationDirectory.mkdirs()
            ) {
                return@withContext MediaStorageResult.Failure(
                    "Could not create media destination directory"
                )
            }

            try {
                val localUris =
                    media.map {
                            item ->

                        val destinationFile =
                            File(
                                destinationDirectory,
                                item.fileName
                            )

                        item.sourceFile.copyTo(
                            target = destinationFile,
                            overwrite = false
                        )

                        Uri.fromFile(
                            destinationFile
                        ).toString()
                    }

                MediaStorageResult.Success(
                    localUris
                )
            } catch (
                exception: Exception
            ) {
                destinationDirectory.deleteRecursively()

                MediaStorageResult.Failure(
                    exception.message
                        ?: "Could not copy media files"
                )
            }
        }

    override suspend fun delete(
        localUris: List<String>
    ) {
        withContext(
            Dispatchers.IO
        ) {
            localUris
                .mapNotNull {
                        rawUri ->

                    Uri.parse(rawUri)
                        .path
                        ?.let(::File)
                        ?.parentFile
                }
                .distinct()
                .forEach {
                    it.deleteRecursively()
                }
        }
    }
}