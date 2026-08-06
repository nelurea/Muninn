package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.AcquisitionQueueDao
import io.github.nelurea.muninn.data.db.AcquisitionQueueEntity

class AcquisitionQueueRepository(
    private val dao: AcquisitionQueueDao
) {

    suspend fun enqueue(
        resolvedCaptureId: Long
    ) {

        if (
            dao.countByResolvedCaptureId(
                resolvedCaptureId
            ) > 0
        ) {
            return
        }

        dao.insert(
            AcquisitionQueueEntity(
                resolvedCaptureId = resolvedCaptureId,
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAll(): List<AcquisitionQueueEntity> {
        return dao.getAll()
    }
}