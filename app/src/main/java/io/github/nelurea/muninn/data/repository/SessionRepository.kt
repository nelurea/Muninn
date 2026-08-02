package io.github.nelurea.muninn.data.repository

import io.github.nelurea.muninn.data.db.SessionDao
import io.github.nelurea.muninn.data.db.SessionEntity

class SessionRepository(
    private val dao: SessionDao
) {

    suspend fun createSession(): Long {
        return dao.insert(
            SessionEntity()
        )
    }

    suspend fun getLatestSession(): SessionEntity? {
        return dao.getLatestSession()
    }

    suspend fun getOrCreateSession(): Long {

        val latest = dao.getLatestSession()

        return latest?.id
            ?: createSession()
    }
}