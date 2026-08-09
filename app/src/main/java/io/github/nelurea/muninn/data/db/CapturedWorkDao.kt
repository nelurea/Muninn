package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Transaction

@Dao
abstract class CapturedWorkDao {

    @Insert
    protected abstract suspend fun insertWork(
        work: CapturedWorkEntity
    ): Long

    @Insert
    protected abstract suspend fun insertMedia(
        media: List<CapturedMediaEntity>
    )

    @Insert
    protected abstract suspend fun insertTags(
        tags: List<CapturedTagEntity>
    )

    @Transaction
    open suspend fun insertCapture(
        work: CapturedWorkEntity,
        media: List<CapturedMediaEntity>,
        tags: List<CapturedTagEntity>
    ): Long {
        val workId = insertWork(work)

        if (media.isNotEmpty()) {
            insertMedia(
                media.map {
                    it.copy(workId = workId)
                }
            )
        }

        if (tags.isNotEmpty()) {
            insertTags(
                tags.map {
                    it.copy(workId = workId)
                }
            )
        }

        return workId
    }
}