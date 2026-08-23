package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.db.SaveEventEntity
import io.github.nelurea.muninn.data.db.SaveEventMediaEntity

data class CaptureIdentitySnapshot(
    val canonical: CapturedWorkWithMedia,
    val mediaByIndex: Map<Int, CapturedMediaEntity>
)

interface CapturePersistence {

    suspend fun <T> inTransaction(block: suspend () -> T): T

    suspend fun saveCapture(
        work: CapturedWorkEntity,
        media: List<CapturedMediaEntity>,
        tags: List<CapturedTagEntity>
    ): Long

    suspend fun getBySourceIdentity(
        sourceType: String,
        sourceId: String
    ): CapturedWorkWithMedia?

    suspend fun getIdentitySnapshot(
        sourceType: String,
        sourceId: String
    ): CaptureIdentitySnapshot?

    suspend fun appendMediaToWork(
        workId: Long,
        media: List<CapturedMediaEntity>
    )

    suspend fun markMediaHighlighted(
        workId: Long,
        mediaIndices: List<Int>
    )

    suspend fun markMediaHighlightedById(mediaIds: List<Long>)

    suspend fun insertSaveEvent(
        event: SaveEventEntity,
        media: List<SaveEventMediaEntity>
    ): Long
}
