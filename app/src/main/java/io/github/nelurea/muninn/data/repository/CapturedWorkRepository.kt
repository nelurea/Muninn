package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkDao
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.db.CapturedWorkPurposeEntity
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity

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
}
