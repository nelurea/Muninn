package io.github.nelurea.muninn.capture.usecase

interface CaptureSessionStore {

    suspend fun getOrCreateSession(): Long

    suspend fun touch(
        sessionId: Long
    )
}
