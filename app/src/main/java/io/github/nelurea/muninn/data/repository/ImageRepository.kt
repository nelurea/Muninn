package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.ImageRecord
import io.github.nelurea.muninn.data.db.ImageRecordDao
import android.content.Context
import android.net.Uri

class ImageRepository(
    private val dao: ImageRecordDao,
    private val context: Context
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
    suspend fun deleteImage(
        id: Long
    ) {

        val image = dao.getById(id)
            ?: return

        context.contentResolver.delete(
            Uri.parse(image.imageUri),
            null,
            null
        )

        dao.deleteById(id)
    }
}