package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.SessionDao
import io.github.nelurea.muninn.data.db.SessionEntity
import io.github.nelurea.muninn.data.db.SessionWithImages
class SessionRepository(
    private val dao: SessionDao
) {

    companion object {
        private const val SESSION_TIMEOUT_MS =
            60 * 1000L // just test
    }

    suspend fun createSession(): Long {
        return dao.insert(
            SessionEntity()
        )
    }

    suspend fun getLatestSession(): SessionEntity? {
        return dao.getLatestSession()
    }

    suspend fun getSession(
        sessionId: Long
    ): SessionWithImages? {

        return dao.getSessionWithImages(
            sessionId
        )
    }

    suspend fun getOrCreateSession(): Long {

        val latest =
            dao.getLatestSession()

        if (latest == null) {
            return createSession()
        }

        val now =
            System.currentTimeMillis()

        val elapsed =
            now - latest.lastActivityAt

        return if (
            elapsed > SESSION_TIMEOUT_MS
        ) {
            createSession()
        } else {
            latest.id
        }
    }
    suspend fun touch(
        sessionId: Long
    ) {
        dao.updateActivity(
            sessionId,
            System.currentTimeMillis()
        )
    }
    suspend fun getSessions():
            List<SessionWithImages> {
        return dao.getSessionsWithImages()
    }
}