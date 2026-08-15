package io.github.nelurea.muninn.capture.discovery

import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DiscoverySaveStatus {
    QUEUED,
    SAVING,
    SAVED,
    FAILED
}

data class DiscoverySaveRequestKey(
    val source: DiscoverySourceId,
    val sourceItemId: String,
    val mode: DiscoveryArtworkSaveMode,
    val selectedMediaIndices: List<Int>
)

data class DiscoverySaveJobState(
    val key: DiscoverySaveRequestKey,
    val status: DiscoverySaveStatus,
    val mediaCount: Int? = null,
    val error: String? = null
)

sealed interface DiscoverySaveEnqueueResult {

    data class Accepted(
        val key: DiscoverySaveRequestKey
    ) : DiscoverySaveEnqueueResult

    data class AlreadyRunning(
        val key: DiscoverySaveRequestKey
    ) : DiscoverySaveEnqueueResult

    data class Failure(
        val error: String
    ) : DiscoverySaveEnqueueResult
}

class DiscoverySaveCoordinator(
    private val saveUseCases: Map<
            DiscoverySourceId,
            DiscoveryArtworkSaveUseCase
            >
) : AutoCloseable {

    private data class SaveRequest(
        val key: DiscoverySaveRequestKey,
        val preview: ArtworkPreview,
        val selectedMediaIndices: Set<Int>,
        val discoveryMode: String?,
        val discoveryQuery: String?
    )

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private val lock =
        Any()

    private val jobs =
        mutableMapOf<
                DiscoverySaveRequestKey,
                Job
                >()

    private val requests =
        mutableMapOf<
                DiscoverySaveRequestKey,
                SaveRequest
                >()

    private val _states =
        MutableStateFlow<
                Map<
                        DiscoverySaveRequestKey,
                        DiscoverySaveJobState
                        >
                >(
            emptyMap()
        )

    val states: StateFlow<
            Map<
                    DiscoverySaveRequestKey,
                    DiscoverySaveJobState
                    >
            > =
        _states.asStateFlow()

    fun enqueue(
        preview: ArtworkPreview,
        selectedMediaIndices: Set<Int>,
        mode: DiscoveryArtworkSaveMode,
        discoveryMode: String?,
        discoveryQuery: String?
    ): DiscoverySaveEnqueueResult {

        if (
            mode ==
            DiscoveryArtworkSaveMode.SELECTED &&
            selectedMediaIndices.isEmpty()
        ) {
            return DiscoverySaveEnqueueResult.Failure(
                "Select at least one page first."
            )
        }

        if (
            saveUseCases[
                preview.source
            ] == null
        ) {
            return DiscoverySaveEnqueueResult.Failure(
                "Saving this Discovery source is not supported."
            )
        }

        val key =
            DiscoverySaveRequestKey(
                source =
                    preview.source,
                sourceItemId =
                    preview.sourceItemId,
                mode =
                    mode,
                selectedMediaIndices =
                    selectedMediaIndices
                        .sorted()
            )

        val request =
            SaveRequest(
                key =
                    key,
                preview =
                    preview,
                selectedMediaIndices =
                    selectedMediaIndices,
                discoveryMode =
                    discoveryMode,
                discoveryQuery =
                    discoveryQuery
            )

        return enqueueRequest(
            request
        )
    }

    fun retry(
        key: DiscoverySaveRequestKey
    ): DiscoverySaveEnqueueResult {

        val request =
            synchronized(
                lock
            ) {
                requests[
                    key
                ]
            }
                ?: return DiscoverySaveEnqueueResult.Failure(
                    "The failed save request is no longer available."
                )

        return enqueueRequest(
            request
        )
    }

    private fun enqueueRequest(
        request: SaveRequest
    ): DiscoverySaveEnqueueResult {

        val saveUseCase =
            saveUseCases[
                request.preview.source
            ]
                ?: return DiscoverySaveEnqueueResult.Failure(
                    "Saving this Discovery source is not supported."
                )

        synchronized(
            lock
        ) {
            val existingJob =
                jobs[
                    request.key
                ]

            if (
                existingJob?.isActive ==
                true
            ) {
                return DiscoverySaveEnqueueResult.AlreadyRunning(
                    request.key
                )
            }

            requests[
                request.key
            ] =
                request

            updateStateLocked(
                DiscoverySaveJobState(
                    key =
                        request.key,
                    status =
                        DiscoverySaveStatus.QUEUED
                )
            )

            val job =
                scope.launch {
                    updateState(
                        DiscoverySaveJobState(
                            key =
                                request.key,
                            status =
                                DiscoverySaveStatus.SAVING
                        )
                    )

                    val result =
                        try {
                            saveUseCase.save(
                                preview =
                                    request.preview,
                                selectedMediaIndices =
                                    request.selectedMediaIndices,
                                mode =
                                    request.key.mode,
                                discoveryMode =
                                    request.discoveryMode,
                                discoveryQuery =
                                    request.discoveryQuery
                            )
                        } catch (
                            exception: Exception
                        ) {
                            SaveCaptureResult.Failure(
                                listOf(
                                    exception.message
                                        ?: "Background save failed."
                                )
                            )
                        }

                    when (
                        result
                    ) {
                        is SaveCaptureResult.Success -> {
                            updateState(
                                DiscoverySaveJobState(
                                    key =
                                        request.key,
                                    status =
                                        DiscoverySaveStatus.SAVED,
                                    mediaCount =
                                        result.mediaCount
                                )
                            )
                        }

                        is SaveCaptureResult.Failure -> {
                            updateState(
                                DiscoverySaveJobState(
                                    key =
                                        request.key,
                                    status =
                                        DiscoverySaveStatus.FAILED,
                                    error =
                                        result.errors
                                            .joinToString(
                                                separator = "\n"
                                            )
                                )
                            )
                        }
                    }

                    synchronized(
                        lock
                    ) {
                        jobs.remove(
                            request.key
                        )
                    }
                }

            jobs[
                request.key
            ] =
                job
        }

        return DiscoverySaveEnqueueResult.Accepted(
            request.key
        )
    }

    private fun updateState(
        state: DiscoverySaveJobState
    ) {
        synchronized(
            lock
        ) {
            updateStateLocked(
                state
            )
        }
    }

    /*
     * Caller must already hold lock.
     */
    private fun updateStateLocked(
        state: DiscoverySaveJobState
    ) {
        _states.value =
            _states.value +
                    (
                            state.key to
                                    state
                            )
    }

    override fun close() {
        scope.cancel()
    }
}
