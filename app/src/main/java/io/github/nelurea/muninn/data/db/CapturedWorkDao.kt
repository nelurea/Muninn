package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.OnConflictStrategy

@Dao
abstract class CapturedWorkDao {

    @Insert
    protected abstract suspend fun insertWork(
        work: CapturedWorkEntity
    ): Long

    @Insert
    protected abstract suspend fun insertMedia(
        media: List<CapturedMediaEntity>
    )

    @Insert
    protected abstract suspend fun insertTags(
        tags: List<CapturedTagEntity>
    )

    @Transaction
    open suspend fun insertCapture(
        work: CapturedWorkEntity,
        media: List<CapturedMediaEntity>,
        tags: List<CapturedTagEntity>
    ): Long {
        val workId = insertWork(work)

        if (media.isNotEmpty()) {
            insertMedia(
                media.map {
                    it.copy(workId = workId)
                }
            )
        }

        if (tags.isNotEmpty()) {
            insertTags(
                tags.map {
                    it.copy(workId = workId)
                }
            )
        }

        return workId
    }

    @Transaction
    @Query(
        """
    SELECT *
    FROM captured_works
    ORDER BY capturedAt DESC
    """
    )
    abstract suspend fun getAllWithMedia():
            List<CapturedWorkWithMedia>

    @Transaction
    @Query(
        """
    SELECT *
    FROM captured_works
    WHERE id = :workId
    """
    )
    abstract suspend fun getWithMediaById(
        workId: Long
    ): CapturedWorkWithMedia?

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertPurposeVocabulary(
        purpose: PurposeVocabularyEntity
    ): Long

    @Query(
        """
    SELECT *
    FROM purpose_vocabulary
    ORDER BY label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getPurposeVocabulary():
            List<PurposeVocabularyEntity>

    @Query(
        """
    SELECT id
    FROM purpose_vocabulary
    WHERE label = :label
    LIMIT 1
    """
    )
    abstract suspend fun getPurposeVocabularyId(
        label: String
    ): Long?

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertCapturedWorkPurpose(
        purpose: CapturedWorkPurposeEntity
    )

    @Query(
        """
    DELETE FROM captured_work_purposes
    WHERE workId = :workId
      AND purposeVocabularyId = :purposeVocabularyId
    """
    )
    abstract suspend fun deleteCapturedWorkPurpose(
        workId: Long,
        purposeVocabularyId: Long
    )

    @Query(
        """
    SELECT purpose_vocabulary.*
    FROM purpose_vocabulary
    INNER JOIN captured_work_purposes
        ON purpose_vocabulary.id =
           captured_work_purposes.purposeVocabularyId
    WHERE captured_work_purposes.workId = :workId
    ORDER BY purpose_vocabulary.label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getPurposesForWork(
        workId: Long
    ): List<PurposeVocabularyEntity>
}
