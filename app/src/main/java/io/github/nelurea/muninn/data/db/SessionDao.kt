package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    @Transaction
    @Query(
        """
        SELECT * FROM sessions
        ORDER BY lastActivityAt DESC
        """
    )
    suspend fun getSessionsWithImages():
            List<SessionWithImages>

    @Transaction
    @Query(
        """
        SELECT * FROM sessions
        WHERE id = :sessionId
        """
    )
    suspend fun getSessionWithImages(
        sessionId: Long
    ): SessionWithImages?

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    suspend fun insertStateVocabulary(
        state: StateVocabularyEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM state_vocabulary
        ORDER BY label COLLATE NOCASE ASC
        """
    )
    suspend fun getStateVocabulary():
            List<StateVocabularyEntity>

    @Query(
        """
        SELECT id
        FROM state_vocabulary
        WHERE label = :label
        LIMIT 1
        """
    )
    suspend fun getStateVocabularyId(
        label: String
    ): Long?

    @Insert(
        onConflict =
            OnConflictStrategy.IGNORE
    )
    suspend fun insertSessionState(
        state: SessionStateEntity
    )

    @Query(
        """
        DELETE FROM session_states
        WHERE sessionId = :sessionId
          AND stateVocabularyId = :stateVocabularyId
        """
    )
    suspend fun deleteSessionState(
        sessionId: Long,
        stateVocabularyId: Long
    )

    @Query(
        """
        SELECT state_vocabulary.*
        FROM state_vocabulary
        INNER JOIN session_states
            ON state_vocabulary.id =
               session_states.stateVocabularyId
        WHERE session_states.sessionId = :sessionId
        ORDER BY state_vocabulary.label COLLATE NOCASE ASC
        """
    )
    suspend fun getStatesForSession(
        sessionId: Long
    ): List<StateVocabularyEntity>
}