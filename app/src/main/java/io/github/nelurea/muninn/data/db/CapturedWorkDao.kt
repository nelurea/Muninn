package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
    WHERE sourceType = :sourceType
      AND sourceId = :sourceId
    ORDER BY capturedAt ASC, id ASC
    LIMIT 1
    """
    )
    abstract suspend fun getBySourceIdentity(
        sourceType: String,
        sourceId: String
    ): CapturedWorkWithMedia?

    @Transaction
    @Query(
        """
        SELECT * FROM captured_works
        WHERE sourceType = :sourceType AND sourceId = :sourceId
        ORDER BY capturedAt ASC, id ASC
        """
    )
    abstract suspend fun getAllBySourceIdentity(
        sourceType: String,
        sourceId: String
    ): List<CapturedWorkWithMedia>

    @Transaction
    open suspend fun appendMediaToWork(
        workId: Long,
        media: List<CapturedMediaEntity>
    ) {
        if (media.isNotEmpty()) {
            insertMedia(
                media.map {
                    it.copy(
                        workId = workId
                    )
                }
            )
        }
    }

    @Query(
        """
    UPDATE captured_media
    SET isHighlighted = 1
    WHERE workId = :workId
      AND mediaIndex IN (:mediaIndices)
    """
    )
    abstract suspend fun markMediaHighlighted(
        workId: Long,
        mediaIndices: List<Int>
    )

    @Query("UPDATE captured_media SET isHighlighted = 1 WHERE id IN (:mediaIds)")
    abstract suspend fun markMediaHighlightedById(mediaIds: List<Long>)
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


    @Query(
        """
        SELECT workId
        FROM captured_work_purposes

        UNION

        SELECT workId
        FROM captured_work_attractions

        UNION

        SELECT workId
        FROM captured_work_responses

        UNION

        SELECT captured_media.workId
        FROM captured_media_attractions
        INNER JOIN captured_media
            ON captured_media.id =
               captured_media_attractions.mediaId

        UNION

        SELECT captured_media.workId
        FROM media_focus
        INNER JOIN captured_media
            ON captured_media.id =
               media_focus.mediaId
        """
    )
    abstract suspend fun getContextualizedWorkIds():
            List<Long>

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

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertAttractionVocabulary(
        attraction: AttractionVocabularyEntity
    ): Long

    @Query(
        """
    SELECT *
    FROM attraction_vocabulary
    ORDER BY dimension ASC, label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getAttractionVocabulary():
            List<AttractionVocabularyEntity>

    @Query(
        """
    SELECT id
    FROM attraction_vocabulary
    WHERE dimension = :dimension
      AND label = :label
    LIMIT 1
    """
    )
    abstract suspend fun getAttractionVocabularyId(
        dimension: String,
        label: String
    ): Long?

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertWorkAttraction(
        attraction: CapturedWorkAttractionEntity
    )

    @Query(
        """
    DELETE FROM captured_work_attractions
    WHERE workId = :workId
      AND attractionVocabularyId = :attractionVocabularyId
    """
    )
    abstract suspend fun deleteWorkAttraction(
        workId: Long,
        attractionVocabularyId: Long
    )

    @Query(
        """
    SELECT attraction_vocabulary.*
    FROM attraction_vocabulary
    INNER JOIN captured_work_attractions
        ON attraction_vocabulary.id =
           captured_work_attractions.attractionVocabularyId
    WHERE captured_work_attractions.workId = :workId
    ORDER BY attraction_vocabulary.dimension ASC,
             attraction_vocabulary.label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getAttractionsForWork(
        workId: Long
    ): List<AttractionVocabularyEntity>

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertResponseVocabulary(
        response: AestheticResponseVocabularyEntity
    ): Long

    @Query(
        """
    SELECT *
    FROM aesthetic_response_vocabulary
    ORDER BY label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getResponseVocabulary():
            List<AestheticResponseVocabularyEntity>

    @Query(
        """
    SELECT id
    FROM aesthetic_response_vocabulary
    WHERE label = :label
    LIMIT 1
    """
    )
    abstract suspend fun getResponseVocabularyId(
        label: String
    ): Long?

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertWorkResponse(
        response: CapturedWorkResponseEntity
    )

    @Query(
        """
    DELETE FROM captured_work_responses
    WHERE workId = :workId
      AND responseVocabularyId = :responseVocabularyId
    """
    )
    abstract suspend fun deleteWorkResponse(
        workId: Long,
        responseVocabularyId: Long
    )

    @Query(
        """
    SELECT aesthetic_response_vocabulary.*
    FROM aesthetic_response_vocabulary
    INNER JOIN captured_work_responses
        ON aesthetic_response_vocabulary.id =
           captured_work_responses.responseVocabularyId
    WHERE captured_work_responses.workId = :workId
    ORDER BY aesthetic_response_vocabulary.label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getResponsesForWork(
        workId: Long
    ): List<AestheticResponseVocabularyEntity>

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    abstract suspend fun insertMediaAttraction(
        attraction: CapturedMediaAttractionEntity
    )

    @Query(
        """
    DELETE FROM captured_media_attractions
    WHERE mediaId = :mediaId
      AND attractionVocabularyId = :attractionVocabularyId
    """
    )
    abstract suspend fun deleteMediaAttraction(
        mediaId: Long,
        attractionVocabularyId: Long
    )

    @Query(
        """
    SELECT attraction_vocabulary.*
    FROM attraction_vocabulary
    INNER JOIN captured_media_attractions
        ON attraction_vocabulary.id =
           captured_media_attractions.attractionVocabularyId
    WHERE captured_media_attractions.mediaId = :mediaId
    ORDER BY attraction_vocabulary.dimension ASC,
             attraction_vocabulary.label COLLATE NOCASE ASC
    """
    )
    abstract suspend fun getAttractionsForMedia(
        mediaId: Long
    ): List<AttractionVocabularyEntity>

    @Insert
    abstract suspend fun insertMediaFocus(
        focus: MediaFocusEntity
    ): Long

    @Query(
        """
    DELETE FROM media_focus
    WHERE id = :focusId
    """
    )
    abstract suspend fun deleteMediaFocus(
        focusId: Long
    )

    @Query(
        """
    SELECT *
    FROM media_focus
    WHERE mediaId = :mediaId
    ORDER BY id ASC
    """
    )
    abstract suspend fun getFocusForMedia(
        mediaId: Long
    ): List<MediaFocusEntity>

    @Update
    protected abstract suspend fun updateWorkMetadata(
        work: CapturedWorkEntity
    )

    @Transaction
    open suspend fun refreshMetadata(
        work: CapturedWorkEntity,
        newTags: List<CapturedTagEntity>
    ) {
        updateWorkMetadata(
            work
        )

        if (newTags.isNotEmpty()) {
            insertTags(
                newTags
            )
        }
    }
}
