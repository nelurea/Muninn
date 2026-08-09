package io.github.nelurea.muninn.capturepackage

import java.io.File

data class LoadedCapturePackage(
    val capturePackage: CapturePackage,
    val packageDirectory: File,
    val mediaFiles: List<File>
)

sealed interface CapturePackageReadResult {

    data class Success(
        val loadedPackage: LoadedCapturePackage
    ) : CapturePackageReadResult

    data class Failure(
        val errors: List<String>
    ) : CapturePackageReadResult
}

object CapturePackageReader {

    private const val MANIFEST_FILE_NAME = "manifest.json"

    fun read(directory: File): CapturePackageReadResult {
        if (!directory.exists()) {
            return CapturePackageReadResult.Failure(
                listOf("Package directory does not exist")
            )
        }

        if (!directory.isDirectory) {
            return CapturePackageReadResult.Failure(
                listOf("Package path is not a directory")
            )
        }

        val manifestFile = File(directory, MANIFEST_FILE_NAME)

        if (!manifestFile.isFile) {
            return CapturePackageReadResult.Failure(
                listOf("manifest.json is missing")
            )
        }

        val json = try {
            manifestFile.readText()
        } catch (exception: Exception) {
            return CapturePackageReadResult.Failure(
                listOf("manifest.json could not be read: ${exception.message}")
            )
        }

        val capturePackage = try {
            CapturePackageParser.parse(json)
        } catch (exception: Exception) {
            return CapturePackageReadResult.Failure(
                listOf("manifest.json could not be parsed: ${exception.message}")
            )
        }

        val errors = CapturePackageValidator
            .validate(capturePackage)
            .toMutableList()

        val fileNames = capturePackage.media.map { it.fileName }

        if (fileNames.distinct().size != fileNames.size) {
            errors += "media file names must be unique"
        }

        val mediaFiles = mutableListOf<File>()

        capturePackage.media.forEach { media ->
            if (
                media.fileName.contains("/") ||
                media.fileName.contains("\\")
            ) {
                errors += "media[${media.index}].fileName must be a file name only"
                return@forEach
            }

            val mediaFile = File(directory, media.fileName)

            when {
                !mediaFile.exists() ->
                    errors += "media[${media.index}] file is missing: ${media.fileName}"

                !mediaFile.isFile ->
                    errors += "media[${media.index}] is not a file: ${media.fileName}"

                mediaFile.length() == 0L ->
                    errors += "media[${media.index}] file is empty: ${media.fileName}"

                else ->
                    mediaFiles += mediaFile
            }
        }

        if (errors.isNotEmpty()) {
            return CapturePackageReadResult.Failure(errors)
        }

        return CapturePackageReadResult.Success(
            LoadedCapturePackage(
                capturePackage = capturePackage,
                packageDirectory = directory,
                mediaFiles = mediaFiles
            )
        )
    }
}