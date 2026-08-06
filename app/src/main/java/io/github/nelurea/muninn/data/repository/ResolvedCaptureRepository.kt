package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.capture.ResolvedCapture
import io.github.nelurea.muninn.data.db.ResolvedCaptureDao
import io.github.nelurea.muninn.data.db.ResolvedCaptureEntity

class ResolvedCaptureRepository(
    private val dao: ResolvedCaptureDao
) {

    suspend fun save(
        capture: ResolvedCapture
    ) {

        dao.insert(
            ResolvedCaptureEntity(
                sourceType = capture.sourceType.name,
                sourceId = capture.sourceId,
                imageIndex = capture.imageIndex,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAll(): List<ResolvedCaptureEntity> {
        return dao.getAll()
    }
}