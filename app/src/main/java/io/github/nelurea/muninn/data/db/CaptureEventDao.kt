package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CaptureEventDao {

    @Insert
    suspend fun insert(
        entity: CaptureEventEntity
    )

    @Query(
        """
        SELECT *
        FROM capture_event
        ORDER BY capturedAt DESC
        """
    )
    suspend fun getAll(): List<CaptureEventEntity>
}