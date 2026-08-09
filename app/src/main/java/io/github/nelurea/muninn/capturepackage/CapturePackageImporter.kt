package io.github.nelurea.muninn.capturepackage

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import java.io.File
import java.util.UUID

sealed interface CapturePackageImportResult {

    data class Success(
        val workId: Long,
        val mediaCount: Int
    ) : CapturePackageImportResult

    data class Failure(
        val errors: List<String>
    ) : CapturePackageImportResult
}

class CapturePackageImporter(
    private val context: Context,
    private val repository: CapturedWorkRepository
) {

    suspend fun import(
        directory: File
    ): CapturePackageImportResult {

        val readResult = CapturePackageReader.read(directory)

        if (readResult is CapturePackageReadResult.Failure) {
            return CapturePackageImportResult.Failure(
                readResult.errors
            )
        }

        val loadedPackage =
            (readResult as CapturePackageReadResult.Success).loadedPackage

        val capturePackage = loadedPackage.capturePackage

        val destinationDirectory = File(
            context.filesDir,
            "captured_media/${UUID.randomUUID()}"
        )

        if (!destinationDirectory.mkdirs()) {
            return CapturePackageImportResult.Failure(
                listOf("Could not create media destination directory")
            )
        }

        val localUris = try {
            loadedPackage.mediaFiles.map { sourceFile ->

                val destinationFile = File(
                    destinationDirectory,
                    sourceFile.name
                )

                sourceFile.copyTo(
                    target = destinationFile,
                    overwrite = false
                )

                Uri.fromFile(destinationFile).toString()
            }
        } catch (exception: Exception) {

            destinationDirectory.deleteRecursively()

            return CapturePackageImportResult.Failure(
                listOf(
                    "Could not copy media files: ${exception.message}"
                )
            )
        }

        val work = CapturedWorkEntity(
            sourceType = capturePackage.source.type,
            sourceId = capturePackage.source.id,
            canonicalUrl = capturePackage.source.canonicalUrl,
            capturedAt = capturePackage.capturedAt,
            authorId = capturePackage.content.author.id,
            authorName = capturePackage.content.author.name,
            title = capturePackage.content.title,
            caption = capturePackage.content.caption
        )

        val media = capturePackage.media.mapIndexed { index, item ->
            CapturedMediaEntity(
                workId = 0,
                mediaIndex = item.index,
                localUri = localUris[index],
                sourceUrl = item.sourceUrl,
                mimeType = item.mimeType,
                fileName = item.fileName
            )
        }

        val tags = capturePackage.content.tags.mapIndexed { index, tag ->
            CapturedTagEntity(
                workId = 0,
                position = index,
                tag = tag
            )
        }

        return try {

            val workId = repository.saveCapture(
                work = work,
                media = media,
                tags = tags
            )

            CapturePackageImportResult.Success(
                workId = workId,
                mediaCount = media.size
            )

        } catch (exception: Exception) {

            destinationDirectory.deleteRecursively()

            CapturePackageImportResult.Failure(
                listOf(
                    "Could not persist capture: ${exception.message}"
                )
            )
        }
    }
}