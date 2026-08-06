package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.PendingCaptureDao
import io.github.nelurea.muninn.data.db.PendingCaptureEntity

class PendingCaptureRepository(
    private val dao: PendingCaptureDao
) {

    suspend fun save(
        sourceUrl: String,
        imageIndex: Int?
    ) {

        dao.insert(
            PendingCaptureEntity(
                sourceUrl = sourceUrl,
                imageIndex = imageIndex,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAll(): List<PendingCaptureEntity> {
        return dao.getAll()
    }
}