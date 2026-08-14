package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.SessionDao
import io.github.nelurea.muninn.data.db.SessionEntity
import io.github.nelurea.muninn.data.db.SessionStateEntity
import io.github.nelurea.muninn.data.db.SessionWithImages
import io.github.nelurea.muninn.data.db.StateVocabularyEntity

data class SessionResolution(
    val sessionId: Long,
    val isNew: Boolean
)

class SessionRepository(
    private val dao: SessionDao
) {

    companion object {
        private const val SESSION_TIMEOUT_MS =
            10 * 60 * 1000L
    }

    private var activeSessionId: Long? =
        null

    suspend fun createSession(): Long {
        val sessionId =
            dao.insert(
                SessionEntity()
            )

        activeSessionId =
            sessionId

        return sessionId
    }

    suspend fun getLatestSession():
            SessionEntity? {
        return dao.getLatestSession()
    }

    suspend fun getSession(
        sessionId: Long
    ): SessionWithImages? {
        return dao.getSessionWithImages(
            sessionId
        )
    }

    suspend fun resolveSession():
            SessionResolution {

        val now =
            System.currentTimeMillis()

        activeSessionId
            ?.let {
                    sessionId ->

                val activeSession =
                    dao.getById(
                        sessionId
                    )

                if (activeSession != null) {
                    val elapsed =
                        now -
                                activeSession.lastActivityAt

                    if (
                        elapsed <=
                        SESSION_TIMEOUT_MS
                    ) {
                        return SessionResolution(
                            sessionId =
                                sessionId,
                            isNew =
                                false
                        )
                    }
                }

                activeSessionId =
                    null
            }

        val latest =
            dao.getLatestSession()

        if (latest == null) {
            val sessionId =
                createSession()

            return SessionResolution(
                sessionId =
                    sessionId,
                isNew =
                    true
            )
        }

        val elapsed =
            now -
                    latest.lastActivityAt

        return if (
            elapsed >
            SESSION_TIMEOUT_MS
        ) {
            val sessionId =
                createSession()

            SessionResolution(
                sessionId =
                    sessionId,
                isNew =
                    true
            )
        } else {
            activeSessionId =
                latest.id

            SessionResolution(
                sessionId =
                    latest.id,
                isNew =
                    false
            )
        }
    }

    suspend fun getOrCreateSession():
            Long {
        return resolveSession()
            .sessionId
    }

    suspend fun touch(
        sessionId: Long
    ) {
        dao.updateActivity(
            sessionId,
            System.currentTimeMillis()
        )

        activeSessionId =
            sessionId
    }

    suspend fun getSessions():
            List<SessionWithImages> {
        return dao.getSessionsWithImages()
    }

    suspend fun getStateVocabulary():
            List<StateVocabularyEntity> {
        return dao.getStateVocabulary()
    }

    suspend fun addStateToSession(
        sessionId: Long,
        label: String
    ) {
        val normalized =
            label.trim()

        if (normalized.isBlank()) {
            return
        }

        val insertedId =
            dao.insertStateVocabulary(
                StateVocabularyEntity(
                    label = normalized
                )
            )

        val stateId =
            if (insertedId != -1L) {
                insertedId
            } else {
                dao.getStateVocabularyId(
                    normalized
                ) ?: return
            }

        dao.insertSessionState(
            SessionStateEntity(
                sessionId =
                    sessionId,
                stateVocabularyId =
                    stateId
            )
        )
    }

    suspend fun removeStateFromSession(
        sessionId: Long,
        stateVocabularyId: Long
    ) {
        dao.deleteSessionState(
            sessionId =
                sessionId,
            stateVocabularyId =
                stateVocabularyId
        )
    }

    suspend fun getStatesForSession(
        sessionId: Long
    ): List<StateVocabularyEntity> {
        return dao.getStatesForSession(
            sessionId
        )
    }
}