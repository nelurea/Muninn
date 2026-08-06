package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AcquisitionQueueDao {

    @Insert
    suspend fun insert(
        entity: AcquisitionQueueEntity
    )

    @Query(
        """
        SELECT *
        FROM acquisition_queue
        ORDER BY createdAt DESC
        """
    )
    suspend fun getAll(): List<AcquisitionQueueEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM acquisition_queue
        WHERE resolvedCaptureId = :resolvedCaptureId
        """
    )
    suspend fun countByResolvedCaptureId(
        resolvedCaptureId: Long
    ): Int
}