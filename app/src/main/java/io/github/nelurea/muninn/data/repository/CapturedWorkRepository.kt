package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedTagEntity
import io.github.nelurea.muninn.data.db.CapturedWorkDao
import io.github.nelurea.muninn.data.db.CapturedWorkEntity

class CapturedWorkRepository(
    private val dao: CapturedWorkDao
) {

    suspend fun saveCapture(
        work: CapturedWorkEntity,
        media: List<CapturedMediaEntity>,
        tags: List<CapturedTagEntity>
    ): Long {
        return dao.insertCapture(
            work = work,
            media = media,
            tags = tags
        )
    }
}