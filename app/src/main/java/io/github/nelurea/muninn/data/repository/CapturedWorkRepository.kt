package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkDao
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.db.CapturedWorkPurposeEntity
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import io.github.nelurea.muninn.data.db.AestheticResponseVocabularyEntity
import io.github.nelurea.muninn.data.db.AttractionVocabularyEntity
import io.github.nelurea.muninn.data.db.CapturedWorkAttractionEntity
import io.github.nelurea.muninn.data.db.CapturedWorkResponseEntity
import io.github.nelurea.muninn.data.db.CapturedMediaAttractionEntity
import io.github.nelurea.muninn.data.db.MediaFocusEntity

class CapturedWorkRepository(
    private val dao: CapturedWorkDao
) {

    suspend fun saveCapture(
        work: CapturedWorkEntity,
        media: List<CapturedMediaEntity>,
        tags: List<CapturedTagEntity>
    ): Long {
        return dao.insertCapture(
            work = work,
            media = media,
            tags = tags
        )
    }

    suspend fun getAllWithMedia():
            List<CapturedWorkWithMedia> {
        return dao.getAllWithMedia()
    }

    suspend fun getWithMediaById(
        workId: Long
    ): CapturedWorkWithMedia? {
        return dao.getWithMediaById(
            workId
        )
    }

    suspend fun getPurposeVocabulary():
            List<PurposeVocabularyEntity> {
        return dao.getPurposeVocabulary()
    }

    suspend fun addPurposeToWork(
        workId: Long,
        label: String
    ) {
        val normalized =
            label.trim()

        if (normalized.isBlank()) {
            return
        }

        val insertedId =
            dao.insertPurposeVocabulary(
                PurposeVocabularyEntity(
                    label = normalized
                )
            )

        val purposeId =
            if (insertedId != -1L) {
                insertedId
            } else {
                dao.getPurposeVocabularyId(
                    normalized
                ) ?: return
            }

        dao.insertCapturedWorkPurpose(
            CapturedWorkPurposeEntity(
                workId = workId,
                purposeVocabularyId =
                    purposeId
            )
        )
    }

    suspend fun removePurposeFromWork(
        workId: Long,
        purposeVocabularyId: Long
    ) {
        dao.deleteCapturedWorkPurpose(
            workId = workId,
            purposeVocabularyId =
                purposeVocabularyId
        )
    }

    suspend fun getPurposesForWork(
        workId: Long
    ): List<PurposeVocabularyEntity> {
        return dao.getPurposesForWork(
            workId
        )
    }

    suspend fun getAttractionVocabulary():
            List<AttractionVocabularyEntity> {
        return dao.getAttractionVocabulary()
    }

    suspend fun addAttractionToWork(
        workId: Long,
        dimension: String,
        label: String
    ) {
        val normalizedDimension =
            dimension.trim()

        val normalizedLabel =
            label.trim()

        if (
            normalizedDimension.isBlank() ||
            normalizedLabel.isBlank()
        ) {
            return
        }

        val insertedId =
            dao.insertAttractionVocabulary(
                AttractionVocabularyEntity(
                    dimension =
                        normalizedDimension,
                    label =
                        normalizedLabel
                )
            )

        val attractionId =
            if (insertedId != -1L) {
                insertedId
            } else {
                dao.getAttractionVocabularyId(
                    dimension =
                        normalizedDimension,
                    label =
                        normalizedLabel
                ) ?: return
            }

        dao.insertWorkAttraction(
            CapturedWorkAttractionEntity(
                workId =
                    workId,
                attractionVocabularyId =
                    attractionId
            )
        )
    }

    suspend fun removeAttractionFromWork(
        workId: Long,
        attractionVocabularyId: Long
    ) {
        dao.deleteWorkAttraction(
            workId =
                workId,
            attractionVocabularyId =
                attractionVocabularyId
        )
    }

    suspend fun getAttractionsForWork(
        workId: Long
    ): List<AttractionVocabularyEntity> {
        return dao.getAttractionsForWork(
            workId
        )
    }

    suspend fun getResponseVocabulary():
            List<AestheticResponseVocabularyEntity> {
        return dao.getResponseVocabulary()
    }

    suspend fun addResponseToWork(
        workId: Long,
        label: String
    ) {
        val normalized =
            label.trim()

        if (normalized.isBlank()) {
            return
        }

        val insertedId =
            dao.insertResponseVocabulary(
                AestheticResponseVocabularyEntity(
                    label =
                        normalized
                )
            )

        val responseId =
            if (insertedId != -1L) {
                insertedId
            } else {
                dao.getResponseVocabularyId(
                    normalized
                ) ?: return
            }

        dao.insertWorkResponse(
            CapturedWorkResponseEntity(
                workId =
                    workId,
                responseVocabularyId =
                    responseId
            )
        )
    }

    suspend fun removeResponseFromWork(
        workId: Long,
        responseVocabularyId: Long
    ) {
        dao.deleteWorkResponse(
            workId =
                workId,
            responseVocabularyId =
                responseVocabularyId
        )
    }

    suspend fun getResponsesForWork(
        workId: Long
    ): List<AestheticResponseVocabularyEntity> {
        return dao.getResponsesForWork(
            workId
        )
    }

    suspend fun addAttractionToMedia(
        mediaId: Long,
        dimension: String,
        label: String
    ) {
        val normalizedDimension =
            dimension.trim()

        val normalizedLabel =
            label.trim()

        if (
            normalizedDimension.isBlank() ||
            normalizedLabel.isBlank()
        ) {
            return
        }

        val insertedId =
            dao.insertAttractionVocabulary(
                AttractionVocabularyEntity(
                    dimension =
                        normalizedDimension,
                    label =
                        normalizedLabel
                )
            )

        val attractionId =
            if (insertedId != -1L) {
                insertedId
            } else {
                dao.getAttractionVocabularyId(
                    dimension =
                        normalizedDimension,
                    label =
                        normalizedLabel
                ) ?: return
            }

        dao.insertMediaAttraction(
            CapturedMediaAttractionEntity(
                mediaId =
                    mediaId,
                attractionVocabularyId =
                    attractionId
            )
        )
    }

    suspend fun removeAttractionFromMedia(
        mediaId: Long,
        attractionVocabularyId: Long
    ) {
        dao.deleteMediaAttraction(
            mediaId =
                mediaId,
            attractionVocabularyId =
                attractionVocabularyId
        )
    }

    suspend fun getAttractionsForMedia(
        mediaId: Long
    ): List<AttractionVocabularyEntity> {
        return dao.getAttractionsForMedia(
            mediaId
        )
    }

    suspend fun addFocusToMedia(
        mediaId: Long,
        attractionVocabularyId: Long?,
        note: String?,
        regionLeft: Float? = null,
        regionTop: Float? = null,
        regionRight: Float? = null,
        regionBottom: Float? = null
    ): Long {
        return dao.insertMediaFocus(
            MediaFocusEntity(
                mediaId =
                    mediaId,
                attractionVocabularyId =
                    attractionVocabularyId,
                note =
                    note
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        },
                regionLeft =
                    regionLeft,
                regionTop =
                    regionTop,
                regionRight =
                    regionRight,
                regionBottom =
                    regionBottom
            )
        )
    }

    suspend fun removeFocus(
        focusId: Long
    ) {
        dao.deleteMediaFocus(
            focusId
        )
    }

    suspend fun getFocusForMedia(
        mediaId: Long
    ): List<MediaFocusEntity> {
        return dao.getFocusForMedia(
            mediaId
        )
    }
}
