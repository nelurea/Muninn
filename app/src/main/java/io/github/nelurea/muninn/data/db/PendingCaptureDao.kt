package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingCaptureDao {

    @Insert
    suspend fun insert(
        entity: PendingCaptureEntity
    )

    @Query(
        """
        SELECT *
        FROM pending_capture
        ORDER BY createdAt DESC
        """
    )
    suspend fun getAll(): List<PendingCaptureEntity>
}