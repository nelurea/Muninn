package io.github.nelurea.muninn.capture.web.pixiv

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import java.io.File

object PixivCaptureMapper {

    fun toCaptureDraft(
        payload: PixivCapturePayload,
        downloadedFiles: List<File>
    ): CaptureDraft {

        require(
            payload.media.size ==
                    downloadedFiles.size
        ) {
            "Media metadata count and downloaded file count do not match"
        }

        val media =
            payload.media.mapIndexed {
                    index,
                    item ->

                CaptureMediaDraft(
                    mediaIndex =
                        item.mediaIndex,

                    sourceUrl =
                        item.sourceUrl,

                    mimeType =
                        item.mimeType
                            ?: "application/octet-stream",

                    fileName =
                        item.fileName,

                    sourceFile =
                        downloadedFiles[index]
                )
            }

        return CaptureDraft(
            sourceType =
                payload.sourceType,

            sourceId =
                payload.sourceId,

            canonicalUrl =
                payload.canonicalUrl,

            capturedAt =
                payload.capturedAt,

            authorId =
                payload.authorId
                    ?: "",

            authorName =
                payload.authorName
                    ?: "",

            title =
                payload.title,

            caption =
                payload.caption
                    ?: "",

            tags =
                payload.tags,

            media =
                media
        )
    }
}