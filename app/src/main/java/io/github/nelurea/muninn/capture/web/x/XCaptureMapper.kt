package io.github.nelurea.muninn.capture.web.x

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import java.io.File

object XCaptureMapper {

    fun toCaptureDraft(
        payload: XCapturePayload,
        downloadedFiles: List<File>,
        discoveryMode: String? = null,
        discoveryQuery: String? = null
    ): CaptureDraft {

        require(
            payload.media.size ==
                    downloadedFiles.size
        ) {
            "Media metadata count and downloaded file count do not match"
        }

        val media =
            payload.media
                .mapIndexed {
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
                            downloadedFiles[
                                index
                            ]
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

            publishedAt =
                payload.publishedAt,

            discoveryMode =
                discoveryMode,

            discoveryQuery =
                discoveryQuery,

            authorId =
                payload.authorId
                    ?: "",

            authorName =
                payload.authorName
                    ?: "",

            authorHandle =
                payload.authorHandle,

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