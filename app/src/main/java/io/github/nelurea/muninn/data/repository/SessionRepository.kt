package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.SessionDao
import io.github.nelurea.muninn.data.db.SessionEntity

class SessionRepository(
    private val dao: SessionDao
) {

    companion object {
        private const val SESSION_TIMEOUT_MS =
            10_000L // just test
    }

    suspend fun createSession(): Long {
        return dao.insert(
            SessionEntity()
        )
    }

    suspend fun getLatestSession(): SessionEntity? {
        return dao.getLatestSession()
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
            now - latest.createdAt

        return if (
            elapsed > SESSION_TIMEOUT_MS
        ) {
            createSession()
        } else {
            latest.id
        }
    }
}