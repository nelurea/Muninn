package io.github.nelurea.muninn.capturepackage

object CapturePackageValidator {

    fun validate(capturePackage: CapturePackage): List<String> {
        val errors = mutableListOf<String>()

        if (capturePackage.schemaVersion != 1) {
            errors += "Unsupported schemaVersion: ${capturePackage.schemaVersion}"
        }

        if (capturePackage.source.type.isBlank()) {
            errors += "source.type is required"
        }

        if (capturePackage.source.id.isBlank()) {
            errors += "source.id is required"
        }

        if (capturePackage.source.canonicalUrl.isBlank()) {
            errors += "source.canonicalUrl is required"
        }

        if (capturePackage.capturedAt.isBlank()) {
            errors += "capturedAt is required"
        }

        if (capturePackage.content.author.id.isBlank()) {
            errors += "content.author.id is required"
        }

        if (capturePackage.content.author.name.isBlank()) {
            errors += "content.author.name is required"
        }

        if (capturePackage.media.isEmpty()) {
            errors += "media must contain at least one item"
        }

        val indexes = capturePackage.media.map { it.index }

        if (indexes.distinct().size != indexes.size) {
            errors += "media indexes must be unique"
        }

        val expectedIndexes = capturePackage.media.indices.toList()

        if (indexes != expectedIndexes) {
            errors += "media indexes must be ordered and contiguous from 0"
        }

        capturePackage.media.forEach { media ->
            if (media.sourceUrl.isBlank()) {
                errors += "media[${media.index}].sourceUrl is required"
            }

            if (media.mimeType.isBlank()) {
                errors += "media[${media.index}].mimeType is required"
            }

            if (media.fileName.isBlank()) {
                errors += "media[${media.index}].fileName is required"
            }
        }

        return errors
    }
}