package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(
        session: SessionEntity
    ): Long

    @Query(
        "SELECT * FROM sessions ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestSession(): SessionEntity?

    @Query(
        "SELECT * FROM sessions WHERE id = :id"
    )
    suspend fun getById(
        id: Long
    ): SessionEntity?

    @Query(
        """
        UPDATE sessions
        SET lastActivityAt = :timestamp
        WHERE id = :sessionId
        """
    )
    suspend fun updateActivity(
        sessionId: Long,
        timestamp: Long
    )
}