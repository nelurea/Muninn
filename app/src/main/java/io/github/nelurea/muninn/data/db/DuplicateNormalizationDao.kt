package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.nelurea.muninn.duplicate.DuplicateNormalizationPlan

@Dao
abstract class DuplicateNormalizationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(journal: DuplicateNormalizationJournalEntity): Long

    @Query(
        """
        SELECT sourceType, sourceId, COUNT(*) AS workCount
        FROM captured_works
        GROUP BY sourceType, sourceId
        HAVING COUNT(*) > 1
        ORDER BY sourceType, sourceId
        """
    )
    abstract suspend fun getDuplicateIdentities(): List<DuplicateIdentity>

    @Query(
        "SELECT * FROM duplicate_normalization_journal WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1"
    )
    abstract suspend fun getByIdentity(sourceType: String, sourceId: String): DuplicateNormalizationJournalEntity?

    @Transaction
    open suspend fun ensureJournal(sourceType: String, sourceId: String, now: Long): DuplicateNormalizationJournalEntity {
        insert(DuplicateNormalizationJournalEntity(sourceType = sourceType, sourceId = sourceId, createdAt = now, updatedAt = now))
        return requireNotNull(getByIdentity(sourceType, sourceId))
    }

    @Query("SELECT * FROM duplicate_normalization_journal WHERE state != 'COMPLETED' ORDER BY id")
    abstract suspend fun getIncomplete(): List<DuplicateNormalizationJournalEntity>

    @Query("SELECT * FROM duplicate_normalization_journal WHERE id = :id")
    abstract suspend fun get(id: Long): DuplicateNormalizationJournalEntity?

    @Query(
        "SELECT * FROM duplicate_normalization_journal WHERE state = 'PLANNED' " +
            "AND (:hasError IS NULL OR (lastError IS NOT NULL) = :hasError) ORDER BY id"
    )
    abstract suspend fun getPlanned(hasError: Boolean?): List<DuplicateNormalizationJournalEntity>

    @Query(
        "UPDATE duplicate_normalization_journal SET lastError = :message, " +
            "verificationDetails = :message, updatedAt = :updatedAt " +
            "WHERE id = :id AND state = 'PLANNED'"
    )
    abstract suspend fun recordNormalizationError(id: Long, message: String, updatedAt: Long): Int

    @Query("SELECT * FROM captured_works WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY capturedAt, id")
    protected abstract suspend fun getWorks(sourceType: String, sourceId: String): List<CapturedWorkEntity>

    @Query(
        """
        SELECT captured_media.* FROM captured_media
        INNER JOIN captured_works ON captured_works.id = captured_media.workId
        WHERE captured_works.sourceType = :sourceType AND captured_works.sourceId = :sourceId
        ORDER BY captured_media.mediaIndex, captured_media.workId, captured_media.id
        """
    )
    protected abstract suspend fun getMedia(sourceType: String, sourceId: String): List<CapturedMediaEntity>

    @Query(
        """
        SELECT media_move_journal.* FROM media_move_journal
        INNER JOIN captured_media ON captured_media.id = media_move_journal.mediaId
        INNER JOIN captured_works ON captured_works.id = captured_media.workId
        WHERE captured_works.sourceType = :sourceType AND captured_works.sourceId = :sourceId
          AND media_move_journal.state != 'COMPLETED'
        ORDER BY media_move_journal.mediaId
        """
    )
    protected abstract suspend fun getActiveMoves(sourceType: String, sourceId: String): List<MediaMoveJournalEntity>

    @Transaction
    open suspend fun snapshot(sourceType: String, sourceId: String) = DuplicateIdentitySnapshot(
        works = getWorks(sourceType, sourceId),
        media = getMedia(sourceType, sourceId),
        activeMoves = getActiveMoves(sourceType, sourceId)
    )

    @Query(
        """
        UPDATE duplicate_normalization_journal
        SET verificationState = :verificationState,
            verificationDetails = :details, lastError = :details,
            updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING'
        """
    )
    protected abstract suspend fun recordVerification(
        id: Long,
        verificationState: String,
        details: String?,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE duplicate_normalization_journal
        SET canonicalWorkId = :canonicalWorkId, planVersion = :planVersion,
            planJson = :planJson, state = 'PLANNED', lastError = NULL,
            updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING' AND verificationState = 'VERIFIED'
        """
    )
    protected abstract suspend fun recordPlan(
        id: Long,
        canonicalWorkId: Long,
        planVersion: Int,
        planJson: String,
        updatedAt: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCleanup(entry: DuplicateCleanupJournalEntity): Long

    @Query("SELECT * FROM duplicate_cleanup_journal WHERE normalizationId = :normalizationId ORDER BY id")
    abstract suspend fun getCleanup(normalizationId: Long): List<DuplicateCleanupJournalEntity>

    @Query("SELECT * FROM duplicate_cleanup_journal WHERE state = 'PENDING' ORDER BY id")
    abstract suspend fun getPendingCleanup(): List<DuplicateCleanupJournalEntity>

    @Query("SELECT * FROM duplicate_cleanup_journal WHERE id = :id")
    abstract suspend fun getCleanupById(id: Long): DuplicateCleanupJournalEntity?

    @Query("SELECT COUNT(*) FROM captured_media WHERE localUri = :uri")
    abstract suspend fun countMediaUsingUri(uri: String): Int

    @Query(
        "UPDATE duplicate_cleanup_journal SET lastError = :error, updatedAt = :updatedAt " +
            "WHERE id = :id AND state = 'PENDING'"
    )
    abstract suspend fun recordCleanupError(id: Long, error: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE duplicate_cleanup_journal
        SET state = 'COMPLETED', lastError = NULL, updatedAt = :updatedAt
        WHERE id = :id AND state = 'PENDING'
        """
    )
    abstract suspend fun markCleanupCompleted(id: Long, updatedAt: Long): Int

    @Transaction
    open suspend fun finalizeVerification(
        journalId: Long,
        expectedSnapshot: DuplicateIdentitySnapshot,
        verification: DuplicateVerification,
        planVersion: Int,
        planJson: String?,
        canonicalWorkId: Long?,
        now: Long
    ): Boolean {
        val journal = get(journalId) ?: return false
        if (journal.state != DuplicateNormalizationState.PENDING) return false
        val current = snapshot(journal.sourceType, journal.sourceId)
        val finalVerification = if (current == expectedSnapshot) verification else {
            DuplicateVerification.rejected(DuplicateVerificationDetails.SNAPSHOT_CHANGED)
        }
        if (finalVerification.state != DuplicateVerificationState.VERIFIED) {
            return recordVerification(journalId, finalVerification.state, finalVerification.details, now) == 1
        }
        if (canonicalWorkId == null || planJson == null) return false
        check(recordVerification(journalId, finalVerification.state, finalVerification.details, now) == 1)
        check(recordPlan(journalId, canonicalWorkId, planVersion, planJson, now) == 1)
        return true
    }

    @Query("SELECT * FROM captured_tags WHERE workId IN (:workIds) ORDER BY workId, position")
    protected abstract suspend fun getTags(workIds: List<Long>): List<CapturedTagEntity>

    @Query("SELECT * FROM captured_work_purposes WHERE workId IN (:workIds)")
    protected abstract suspend fun getPurposes(workIds: List<Long>): List<CapturedWorkPurposeEntity>

    @Query("SELECT * FROM captured_work_attractions WHERE workId IN (:workIds)")
    protected abstract suspend fun getWorkAttractions(workIds: List<Long>): List<CapturedWorkAttractionEntity>

    @Query("SELECT * FROM captured_work_responses WHERE workId IN (:workIds)")
    protected abstract suspend fun getResponses(workIds: List<Long>): List<CapturedWorkResponseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTags(items: List<CapturedTagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPurposes(items: List<CapturedWorkPurposeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertWorkAttractions(items: List<CapturedWorkAttractionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertResponses(items: List<CapturedWorkResponseEntity>)

    @Query("DELETE FROM captured_tags WHERE workId IN (:workIds)")
    protected abstract suspend fun deleteTags(workIds: List<Long>)

    @Update
    protected abstract suspend fun updateWork(work: CapturedWorkEntity)

    @Query("UPDATE captured_media SET isHighlighted = :highlighted WHERE id = :mediaId")
    protected abstract suspend fun updateHighlight(mediaId: Long, highlighted: Boolean)

    @Query("UPDATE captured_media SET workId = :canonicalWorkId WHERE id = :mediaId")
    protected abstract suspend fun rehomeMedia(mediaId: Long, canonicalWorkId: Long)

    @Query(
        "INSERT OR IGNORE INTO captured_media_attractions (mediaId, attractionVocabularyId) " +
            "SELECT :canonicalMediaId, attractionVocabularyId FROM captured_media_attractions WHERE mediaId IN (:mediaIds)"
    )
    protected abstract suspend fun mergeMediaAttractions(canonicalMediaId: Long, mediaIds: List<Long>)

    @Query("UPDATE media_focus SET mediaId = :canonicalMediaId WHERE mediaId IN (:duplicateMediaIds)")
    protected abstract suspend fun repointMediaFocus(canonicalMediaId: Long, duplicateMediaIds: List<Long>)

    @Query("UPDATE save_event_media SET capturedMediaId = :canonicalMediaId WHERE capturedMediaId IN (:duplicateMediaIds)")
    protected abstract suspend fun repointSaveEventMedia(canonicalMediaId: Long, duplicateMediaIds: List<Long>)

    @Query("SELECT COUNT(*) FROM save_events WHERE legacyWorkId = :workId")
    protected abstract suspend fun legacyEventCount(workId: Long): Int

    @Insert
    protected abstract suspend fun insertSaveEvent(event: SaveEventEntity): Long

    @Insert
    protected abstract suspend fun insertSaveEventMedia(items: List<SaveEventMediaEntity>)

    @Query("UPDATE save_events SET canonicalWorkId = :canonicalWorkId WHERE canonicalWorkId IN (:workIds)")
    protected abstract suspend fun repointSaveEvents(canonicalWorkId: Long, workIds: List<Long>)

    @Query("SELECT COUNT(*) FROM captured_media WHERE localUri = :uri AND id NOT IN (:excludedMediaIds)")
    protected abstract suspend fun countOtherMediaUsingUri(uri: String, excludedMediaIds: List<Long>): Int

    @Query("DELETE FROM captured_media WHERE id IN (:mediaIds)")
    protected abstract suspend fun deleteMedia(mediaIds: List<Long>)

    @Query("DELETE FROM captured_works WHERE id IN (:workIds)")
    protected abstract suspend fun deleteWorks(workIds: List<Long>)

    @Query(
        "UPDATE duplicate_normalization_journal SET state = 'COMPLETED', lastError = NULL, updatedAt = :updatedAt " +
            "WHERE id = :id AND state = 'PLANNED'"
    )
    protected abstract suspend fun markCompleted(id: Long, updatedAt: Long): Int

    @Transaction
    open suspend fun applyPlan(
        journalId: Long,
        plan: DuplicateNormalizationPlan,
        now: Long
    ): DuplicateNormalizationApplyResult {
        val journal = get(journalId) ?: return DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED
        if (journal.state != DuplicateNormalizationState.PLANNED || journal.planVersion != 2 ||
            journal.sourceType != plan.sourceType || journal.sourceId != plan.sourceId ||
            journal.canonicalWorkId != plan.canonicalWorkId
        ) return DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED
        val current = snapshot(plan.sourceType, plan.sourceId)
        if (current.activeMoves.isNotEmpty()) return DuplicateNormalizationApplyResult.MEDIA_MOVE_IN_PROGRESS
        val plannedWorkIds = (listOf(plan.canonicalWorkId) + plan.duplicateWorkIds).toSet()
        if (current.works.map { it.id }.toSet() != plannedWorkIds) {
            return DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED
        }
        val plannedMedia = plan.mediaSnapshot.associateBy { it.id }
        if (current.media.map { it.id }.toSet() != plannedMedia.keys || current.media.any { item ->
                plannedMedia[item.id]?.let {
                    it.workId != item.workId || it.mediaIndex != item.mediaIndex || it.localUri != item.localUri
                } != false
            }
        ) return DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED

        val worksById = current.works.associateBy { it.id }
        val canonical = worksById.getValue(plan.canonicalWorkId)
        val orderedWorks = current.works.sortedWith(compareBy({ it.capturedAt }, { it.id }))
        fun firstText(select: (CapturedWorkEntity) -> String?): String? =
            orderedWorks.firstNotNullOfOrNull { select(it)?.takeIf(String::isNotBlank) }
        updateWork(
            canonical.copy(
                canonicalUrl = canonical.canonicalUrl.ifBlank { firstText { it.canonicalUrl }.orEmpty() },
                publishedAt = canonical.publishedAt?.takeIf(String::isNotBlank) ?: firstText { it.publishedAt },
                discoveryMode = canonical.discoveryMode?.takeIf(String::isNotBlank) ?: firstText { it.discoveryMode },
                discoveryQuery = canonical.discoveryQuery?.takeIf(String::isNotBlank) ?: firstText { it.discoveryQuery },
                authorId = canonical.authorId.ifBlank { firstText { it.authorId }.orEmpty() },
                authorName = canonical.authorName.ifBlank { firstText { it.authorName }.orEmpty() },
                authorHandle = canonical.authorHandle?.takeIf(String::isNotBlank) ?: firstText { it.authorHandle },
                title = canonical.title?.takeIf(String::isNotBlank) ?: firstText { it.title },
                caption = canonical.caption.ifBlank { firstText { it.caption }.orEmpty() },
                sessionId = canonical.sessionId
            )
        )

        val workIds = orderedWorks.map { it.id }
        val mergedTags = getTags(workIds).sortedWith(
            compareBy<CapturedTagEntity>({ workIds.indexOf(it.workId) }, { it.position })
        ).distinctBy { it.tag }.mapIndexed { index, tag -> tag.copy(workId = canonical.id, position = index) }
        val purposes = getPurposes(workIds).map { it.copy(workId = canonical.id) }
        val attractions = getWorkAttractions(workIds).map { it.copy(workId = canonical.id) }
        val responses = getResponses(workIds).map { it.copy(workId = canonical.id) }
        deleteTags(workIds)
        insertTags(mergedTags)
        insertPurposes(purposes)
        insertWorkAttractions(attractions)
        insertResponses(responses)

        for (work in orderedWorks) {
            if (legacyEventCount(work.id) == 0) {
                val eventId = insertSaveEvent(
                    SaveEventEntity(
                        sourceType = work.sourceType, sourceId = work.sourceId, canonicalUrl = work.canonicalUrl,
                        savedAt = work.capturedAt, origin = SaveEventOrigin.LEGACY, legacyWorkId = work.id,
                        sessionId = work.sessionId, canonicalWorkId = canonical.id,
                        discoveryMode = work.discoveryMode, discoveryQuery = work.discoveryQuery, saveKind = SaveKind.LEGACY
                    )
                )
                val eventMedia = current.media.filter { it.workId == work.id }.map { media ->
                    SaveEventMediaEntity(
                        saveEventId = eventId, capturedMediaId = media.id, mediaIndex = media.mediaIndex,
                        localUri = media.localUri, sourceUrl = media.sourceUrl, mimeType = media.mimeType,
                        fileName = media.fileName, wasHighlighted = media.isHighlighted, isLegacyBackfill = true
                    )
                }
                if (eventMedia.isNotEmpty()) insertSaveEventMedia(eventMedia)
            }
        }
        repointSaveEvents(canonical.id, workIds)

        val duplicateMediaIds = plan.media.flatMap { it.duplicateMediaIds }
        for (group in plan.media) {
            val groupIds = listOf(group.canonicalMediaId) + group.duplicateMediaIds
            val highlighted = current.media.filter { it.id in groupIds }.any { it.isHighlighted }
            rehomeMedia(group.canonicalMediaId, canonical.id)
            updateHighlight(group.canonicalMediaId, highlighted)
            mergeMediaAttractions(group.canonicalMediaId, groupIds)
            if (group.duplicateMediaIds.isNotEmpty()) {
                repointMediaFocus(group.canonicalMediaId, group.duplicateMediaIds)
                repointSaveEventMedia(group.canonicalMediaId, group.duplicateMediaIds)
            }
        }
        duplicateMediaIds.mapNotNull(plannedMedia::get).groupBy { it.localUri }.forEach { (uri, media) ->
            if (countOtherMediaUsingUri(uri, duplicateMediaIds) == 0) {
                insertCleanup(
                    DuplicateCleanupJournalEntity(
                        normalizationId = journalId, capturedMediaId = media.minOf { it.id },
                        targetUri = uri, updatedAt = now
                    )
                )
            }
        }
        if (duplicateMediaIds.isNotEmpty()) deleteMedia(duplicateMediaIds)
        if (plan.duplicateWorkIds.isNotEmpty()) deleteWorks(plan.duplicateWorkIds)
        check(markCompleted(journalId, now) == 1) { "Normalization completion CAS failed" }
        return DuplicateNormalizationApplyResult.APPLIED
    }
}

enum class DuplicateNormalizationApplyResult {
    APPLIED,
    SNAPSHOT_CHANGED,
    MEDIA_MOVE_IN_PROGRESS
}

data class DuplicateIdentity(val sourceType: String, val sourceId: String, val workCount: Int)

data class DuplicateIdentitySnapshot(
    val works: List<CapturedWorkEntity>,
    val media: List<CapturedMediaEntity>,
    val activeMoves: List<MediaMoveJournalEntity>
)

data class DuplicateVerification(val state: String, val details: String?) {
    companion object {
        fun verified() = DuplicateVerification(DuplicateVerificationState.VERIFIED, null)
        fun rejected(reason: String) = DuplicateVerification(DuplicateVerificationState.REJECTED, reason)
    }
}

object DuplicateVerificationDetails {
    const val SIZE_MISMATCH = "SIZE_MISMATCH"
    const val HASH_MISMATCH = "HASH_MISMATCH"
    const val UNREADABLE = "UNREADABLE"
    const val SNAPSHOT_CHANGED = "SNAPSHOT_CHANGED"
    const val MEDIA_MOVE_IN_PROGRESS = "MEDIA_MOVE_IN_PROGRESS"
    const val INVALID_PLAN = "INVALID_PLAN"
    const val DB_APPLY_FAILED = "DB_APPLY_FAILED"
}
