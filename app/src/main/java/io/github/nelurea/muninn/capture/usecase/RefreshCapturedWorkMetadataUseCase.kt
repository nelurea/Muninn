package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository

sealed interface RefreshCapturedWorkMetadataResult {

    data class Success(
        val workId: Long,
        val addedTagCount: Int
    ) : RefreshCapturedWorkMetadataResult

    data class Failure(
        val error: String
    ) : RefreshCapturedWorkMetadataResult
}

class RefreshCapturedWorkMetadataUseCase(
    private val repository: CapturedWorkRepository
) {

    suspend fun refreshX(
        workId: Long,
        payload: XCapturePayload
    ): RefreshCapturedWorkMetadataResult {

        val current =
            repository.getWithMediaById(
                workId
            )
                ?: return RefreshCapturedWorkMetadataResult.Failure(
                    "Saved work not found"
                )

        if (
            current.work.sourceType != "x"
        ) {
            return RefreshCapturedWorkMetadataResult.Failure(
                "Metadata refresh currently supports X only"
            )
        }

        if (
            current.work.sourceId !=
            payload.sourceId
        ) {
            return RefreshCapturedWorkMetadataResult.Failure(
                "Refreshed X post does not match the saved work"
            )
        }

        val existingTagNames =
            current.tags
                .map {
                    it.tag
                }
                .toSet()

        val incomingTags =
            payload.tags
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val missingTags =
            incomingTags
                .filterNot {
                    it in existingTagNames
                }

        val nextTagPosition =
            (
                current.tags
                    .maxOfOrNull {
                        it.position
                    }
                    ?: -1
            ) + 1

        val refreshedWork =
            current.work.copy(
                canonicalUrl =
                    payload.canonicalUrl
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?: current.work.canonicalUrl,

                publishedAt =
                    payload.publishedAt
                        ?: current.work.publishedAt,

                authorId =
                    payload.authorId
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: current.work.authorId,

                authorName =
                    payload.authorName
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: current.work.authorName,

                authorHandle =
                    payload.authorHandle
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: current.work.authorHandle,

                title =
                    payload.title
                        ?: current.work.title,

                caption =
                    payload.caption
                        ?: current.work.caption
            )

        return try {
            repository.refreshMetadata(
                work =
                    refreshedWork,
                newTags =
                    missingTags
                        .mapIndexed {
                                index,
                                tag ->

                            CapturedTagEntity(
                                workId =
                                    workId,
                                position =
                                    nextTagPosition + index,
                                tag =
                                    tag
                            )
                        }
            )

            RefreshCapturedWorkMetadataResult.Success(
                workId =
                    workId,
                addedTagCount =
                    missingTags.size
            )
        } catch (
            exception: Exception
        ) {
            RefreshCapturedWorkMetadataResult.Failure(
                exception.message
                    ?: "Could not refresh saved metadata"
            )
        }
    }
}
