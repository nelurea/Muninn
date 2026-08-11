package io.github.nelurea.muninn.capturepackage

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft

object CapturePackageMapper {

    fun toCaptureDraft(
        loadedPackage: LoadedCapturePackage
    ): CaptureDraft {

        val capturePackage = loadedPackage.capturePackage

        val media = capturePackage.media.mapIndexed { index, item ->
            CaptureMediaDraft(
                mediaIndex = item.index,
                sourceUrl = item.sourceUrl,
                mimeType = item.mimeType,
                fileName = item.fileName,
                sourceFile = loadedPackage.mediaFiles[index]
            )
        }

        return CaptureDraft(
            sourceType = capturePackage.source.type,
            sourceId = capturePackage.source.id,
            canonicalUrl = capturePackage.source.canonicalUrl,
            capturedAt = capturePackage.capturedAt,
            authorId = capturePackage.content.author.id,
            authorName = capturePackage.content.author.name,
            title = capturePackage.content.title,
            caption = capturePackage.content.caption,
            tags = capturePackage.content.tags,
            media = media
        )
    }
}
