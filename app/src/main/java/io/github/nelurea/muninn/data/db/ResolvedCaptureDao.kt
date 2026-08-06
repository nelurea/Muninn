package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ResolvedCaptureDao {

    @Insert
    suspend fun insert(
        entity: ResolvedCaptureEntity
    )

    @Query(
        """
        SELECT *
        FROM resolved_capture
        ORDER BY createdAt DESC
        """
    )
    suspend fun getAll(): List<ResolvedCaptureEntity>

    @Query("""
SELECT COUNT(*)
FROM resolved_capture
WHERE pendingCaptureId = :pendingCaptureId
""")
    suspend fun countByPendingCaptureId(
        pendingCaptureId: Long
    ): Int
}