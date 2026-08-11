package io.github.nelurea.muninn.capturepackage

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import java.io.File
import java.util.UUID
import io.github.nelurea.muninn.data.repository.SessionRepository

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
    private val repository: CapturedWorkRepository,
    private val sessionRepository: SessionRepository
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

        val draft = CapturePackageMapper.toCaptureDraft(
            loadedPackage
        )

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
            draft.media.map { item ->

                val destinationFile = File(
                    destinationDirectory,
                    item.fileName
                )

                item.sourceFile.copyTo(
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

        val sessionId = try {
            sessionRepository.getOrCreateSession()
        } catch (exception: Exception) {

            destinationDirectory.deleteRecursively()

            return CapturePackageImportResult.Failure(
                listOf(
                    "Could not resolve session: ${exception.message}"
                )
            )
        }

        val work = CapturedWorkEntity(
            sourceType = draft.sourceType,
            sourceId = draft.sourceId,
            canonicalUrl = draft.canonicalUrl,
            capturedAt = draft.capturedAt,
            authorId = draft.authorId,
            authorName = draft.authorName,
            title = draft.title,
            caption = draft.caption,
            sessionId = sessionId
        )

        val media = draft.media.mapIndexed { index, item ->
            CapturedMediaEntity(
                workId = 0,
                mediaIndex = item.mediaIndex,
                localUri = localUris[index],
                sourceUrl = item.sourceUrl,
                mimeType = item.mimeType,
                fileName = item.fileName
            )
        }

        val tags = draft.tags.mapIndexed { index, tag ->
            CapturedTagEntity(
                workId = 0,
                position = index,
                tag = tag
            )
        }

        val workId = try {

            repository.saveCapture(
                work = work,
                media = media,
                tags = tags
            )

        } catch (exception: Exception) {

            destinationDirectory.deleteRecursively()

            return CapturePackageImportResult.Failure(
                listOf(
                    "Could not persist capture: ${exception.message}"
                )
            )
        }

        try {
            sessionRepository.touch(
                sessionId
            )
        } catch (exception: Exception) {
            // Capture itself has already been persisted successfully.
            // Failure to update session activity must not delete captured media.
        }

        return CapturePackageImportResult.Success(
            workId = workId,
            mediaCount = media.size
        )
    }
}
