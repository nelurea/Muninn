package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.ImageRecord
import io.github.nelurea.muninn.data.db.ImageRecordDao

class ImageRepository(
    private val dao: ImageRecordDao
) {

    fun getImages() = dao.getAll()

    suspend fun save(uri: String) {

        dao.insert(
            ImageRecord(
                imageUri = uri
            )
        )
    }

    suspend fun getImage(
        id: Long
    ): ImageRecord? {
        return dao.getById(id)
    }
}