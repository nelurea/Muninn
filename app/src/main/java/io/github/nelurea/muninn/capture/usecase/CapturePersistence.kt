package io.github.nelurea.muninn.capture.usecase

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia

interface CapturePersistence {

    suspend fun saveCapture(
        work: CapturedWorkEntity,
        media: List<CapturedMediaEntity>,
        tags: List<CapturedTagEntity>
    ): Long

    suspend fun getBySourceIdentity(
        sourceType: String,
        sourceId: String
    ): CapturedWorkWithMedia?

    suspend fun appendMediaToWork(
        workId: Long,
        media: List<CapturedMediaEntity>
    )

    suspend fun markMediaHighlighted(
        workId: Long,
        mediaIndices: List<Int>
    )
}
