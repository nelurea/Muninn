package io.github.nelurea.muninn.capture.web.x

import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import java.io.File

object XCaptureMapper {

    fun toCaptureDraft(
        payload: XCapturePayload,
        downloadedFiles: List<File>,
        discoveryMode: String? = null,
        discoveryQuery: String? = null,
        highlightedMediaIndices: Set<Int> =
            emptySet()
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
                            canonicalFileName(
                                payload.sourceId,
                                item
                            ),

                        sourceFile =
                            downloadedFiles[
                                index
                            ],

                        isHighlighted =
                            item.mediaIndex in
                                    highlightedMediaIndices
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
                classifiedXTags(
                    payload
                ),

            media =
                media
        )
    }

    private fun canonicalFileName(
        sourceId: String,
        media: XCaptureMediaPayload
    ): String {
        val extension =
            media.fileName
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]+")) }
                ?: extensionForMimeType(media.mimeType)

        return "x-$sourceId-p${media.mediaIndex}.$extension"
    }

    private fun extensionForMimeType(mimeType: String?): String =
        when (mimeType?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            "video/mp4" -> "mp4"
            else -> "bin"
        }
}
