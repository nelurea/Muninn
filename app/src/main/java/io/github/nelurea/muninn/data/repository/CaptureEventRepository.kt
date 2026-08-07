package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.CaptureEventDao
import io.github.nelurea.muninn.data.db.CaptureEventEntity

class CaptureEventRepository(
    private val dao: CaptureEventDao
) {

    suspend fun save(
        sourceType: String,
        sourceId: String,
        imageIndex: Int?
    ) {

        dao.insert(
            CaptureEventEntity(
                sourceType = sourceType,
                sourceId = sourceId,
                imageIndex = imageIndex,
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAll(): List<CaptureEventEntity> {
        return dao.getAll()
    }
}