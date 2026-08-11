package io.github.nelurea.muninn.capturepackage

import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import java.io.File

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
    private val saveCaptureUseCase: SaveCaptureUseCase
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

        return when (
            val result = saveCaptureUseCase.save(draft)
        ) {
            is SaveCaptureResult.Success ->
                CapturePackageImportResult.Success(
                    workId = result.workId,
                    mediaCount = result.mediaCount
                )

            is SaveCaptureResult.Failure ->
                CapturePackageImportResult.Failure(
                    errors = result.errors
                )
        }
    }
}