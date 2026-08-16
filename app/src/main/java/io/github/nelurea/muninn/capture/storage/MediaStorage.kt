package io.github.nelurea.muninn.capture.storage

import io.github.nelurea.muninn.capture.model.CaptureMediaDraft

sealed interface MediaStorageResult {

    data class Success(
        val localUris: List<String>
    ) : MediaStorageResult

    data class Failure(
        val error: String
    ) : MediaStorageResult
}

interface MediaStorage {

    suspend fun store(
        media: List<CaptureMediaDraft>
    ): MediaStorageResult

    suspend fun delete(
        localUris: List<String>
    )
}