package io.github.nelurea.muninn.capturepackage.transport

import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class CapturePackageArchiveExtractor(
    private val cacheDirectory: File
) {

    fun extract(
        archiveFile: File
    ): File {
        require(archiveFile.isFile) {
            "CapturePackage archive does not exist: ${archiveFile.absolutePath}"
        }

        val destinationDirectory =
            File(
                cacheDirectory,
                UUID.randomUUID().toString()
            )

        check(destinationDirectory.mkdirs()) {
            "Failed to create extraction directory"
        }

        try {
            extractArchive(
                archiveFile = archiveFile,
                destinationDirectory = destinationDirectory
            )

            return destinationDirectory
        } catch (error: Exception) {
            destinationDirectory.deleteRecursively()
            throw error
        }
    }

    private fun extractArchive(
        archiveFile: File,
        destinationDirectory: File
    ) {
        val destinationRoot =
            destinationDirectory.canonicalFile

        var entryCount = 0
        var totalBytes = 0L

        archiveFile
            .inputStream()
            .buffered()
            .use { fileInput ->
                ZipInputStream(fileInput).use { zipInput ->

                    while (true) {
                        val entry =
                            zipInput.nextEntry
                                ?: break

                        entryCount++

                        require(entryCount <= MAX_ENTRY_COUNT) {
                            "CapturePackage contains too many ZIP entries"
                        }

                        val outputFile =
                            File(
                                destinationDirectory,
                                entry.name
                            ).canonicalFile

                        require(
                            outputFile.path ==
                                    destinationRoot.path ||
                                    outputFile.path.startsWith(
                                        destinationRoot.path +
                                                File.separator
                                    )
                        ) {
                            "Unsafe ZIP entry path: ${entry.name}"
                        }

                        if (entry.isDirectory) {
                            require(
                                outputFile.mkdirs() ||
                                        outputFile.isDirectory
                            ) {
                                "Failed to create directory: ${entry.name}"
                            }
                        } else {
                            val parent =
                                outputFile.parentFile

                            require(
                                parent != null &&
                                        (
                                                parent.mkdirs() ||
                                                        parent.isDirectory
                                                )
                            ) {
                                "Failed to create parent directory"
                            }

                            outputFile
                                .outputStream()
                                .buffered()
                                .use { output ->

                                    val buffer =
                                        ByteArray(DEFAULT_BUFFER_SIZE)

                                    while (true) {
                                        val read =
                                            zipInput.read(buffer)

                                        if (read < 0) {
                                            break
                                        }

                                        totalBytes += read

                                        require(
                                            totalBytes <=
                                                    MAX_EXTRACTED_BYTES
                                        ) {
                                            "CapturePackage is too large"
                                        }

                                        output.write(
                                            buffer,
                                            0,
                                            read
                                        )
                                    }
                                }
                        }

                        zipInput.closeEntry()
                    }
                }
            }

        require(entryCount > 0) {
            "CapturePackage archive is empty"
        }
    }

    companion object {
        private const val MAX_ENTRY_COUNT = 100

        private const val MAX_EXTRACTED_BYTES =
            250L * 1024L * 1024L
    }
}