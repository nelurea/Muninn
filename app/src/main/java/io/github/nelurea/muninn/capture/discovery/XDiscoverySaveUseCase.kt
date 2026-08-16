package io.github.nelurea.muninn.capture.discovery

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.capture.web.x.XCaptureMapper
import io.github.nelurea.muninn.capture.web.x.XMediaDownloadResult
import io.github.nelurea.muninn.capture.web.x.XMediaDownloader
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import io.github.nelurea.muninn.discovery.x.XDiscoveryObservationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class XDiscoverySaveUseCase(
    private val context: Context,
    private val saveCaptureUseCase: SaveCaptureUseCase,
    private val observationStore:
    XDiscoveryObservationStore =
        XDiscoveryObservationStore
) : DiscoveryArtworkSaveUseCase {

    override val sourceId =
        DiscoverySourceId.X

    override suspend fun save(
        preview: ArtworkPreview,
        selectedMediaIndices: Set<Int>,
        mode: DiscoveryArtworkSaveMode,
        discoveryMode: String?,
        discoveryQuery: String?
    ): SaveCaptureResult =
        withContext(
            Dispatchers.IO
        ) {
            val totalStartedAt =
                SystemClock.elapsedRealtime()

            if (
                preview.source !=
                DiscoverySourceId.X
            ) {
                return@withContext SaveCaptureResult.Failure(
                    listOf(
                        "XDiscoverySaveUseCase only supports X previews."
                    )
                )
            }

            val observedPayload =
                observationStore
                    .findBySourceId(
                        preview.sourceItemId
                    )
                    ?: return@withContext SaveCaptureResult.Failure(
                        listOf(
                            "Observed X post is no longer available."
                        )
                    )

            val selectedPayload =
                when (
                    mode
                ) {
                    DiscoveryArtworkSaveMode.ALL -> {
                        observedPayload
                    }

                    DiscoveryArtworkSaveMode.SELECTED -> {
                        if (
                            selectedMediaIndices.isEmpty()
                        ) {
                            return@withContext SaveCaptureResult.Failure(
                                listOf(
                                    "Select at least one page first."
                                )
                            )
                        }

                        observedPayload.copy(
                            media =
                                observedPayload
                                    .media
                                    .filter {
                                        it.mediaIndex in
                                                selectedMediaIndices
                                    }
                        )
                    }
                }

            if (
                selectedPayload.media.isEmpty()
            ) {
                return@withContext SaveCaptureResult.Failure(
                    listOf(
                        "No X media was selected."
                    )
                )
            }
            val requestedMediaIndices =
                selectedPayload
                    .media
                    .map {
                        it.mediaIndex
                    }
                    .toSet()

            val prepareStartedAt =
                SystemClock.elapsedRealtime()

            val preparation =
                try {
                    saveCaptureUseCase.prepareMediaSave(
                        sourceType =
                            "x",
                        sourceId =
                            preview.sourceItemId,
                        requestedMediaIndices =
                            requestedMediaIndices,
                        highlightedMediaIndices =
                            selectedMediaIndices
                    )
                } catch (
                    exception: Exception
                ) {
                    return@withContext SaveCaptureResult.Failure(
                        listOf(
                            "Could not check existing capture: ${exception.message}"
                        )
                    )
                }

            Log.d(
                "Muninn/SavePerf",
                "x source=${preview.sourceItemId} " +
                    "prepare=${SystemClock.elapsedRealtime() - prepareStartedAt}ms"
            )

            if (
                preparation.missingMediaIndices.isEmpty()
            ) {
                Log.d(
                    "Muninn/SavePerf",
                    "x source=${preview.sourceItemId} " +
                        "alreadySaved=true " +
                        "total=${SystemClock.elapsedRealtime() - totalStartedAt}ms"
                )
                return@withContext SaveCaptureResult.Success(
                    workId =
                        preparation.workId
                            ?: 0L,
                    mediaCount =
                        0
                )
            }

            val missingPayload =
                selectedPayload.copy(
                    media =
                        selectedPayload
                            .media
                            .filter {
                                it.mediaIndex in
                                    preparation.missingMediaIndices
                            }
                )

            val downloader =
                XMediaDownloader(
                    context
                )

            val downloadStartedAt =
                SystemClock.elapsedRealtime()

            when (
                val downloadResult =
                    downloader.download(
                        payload =
                            missingPayload,
                        userAgent =
                            USER_AGENT
                    )
            ) {
                is XMediaDownloadResult.Success -> {
                    Log.d(
                        "Muninn/SavePerf",
                        "x source=${preview.sourceItemId} " +
                            "download=${SystemClock.elapsedRealtime() - downloadStartedAt}ms " +
                            "pages=${downloadResult.files.size}"
                    )

                    val temporaryDirectory =
                        downloadResult
                            .files
                            .firstOrNull()
                            ?.parentFile

                    try {
                        val draft =
                            XCaptureMapper
                                .toCaptureDraft(
                                    payload =
                                        missingPayload,
                                    downloadedFiles =
                                        downloadResult.files,
                                    discoveryMode =
                                        discoveryMode,
                                    discoveryQuery =
                                        discoveryQuery,
                                    highlightedMediaIndices =
                                        selectedMediaIndices
                                )

                        val persistStartedAt =
                            SystemClock.elapsedRealtime()

                        val result =
                            saveCaptureUseCase.save(
                                draft
                            )

                        Log.d(
                            "Muninn/SavePerf",
                            "x source=${preview.sourceItemId} " +
                                "persist=${SystemClock.elapsedRealtime() - persistStartedAt}ms " +
                                "total=${SystemClock.elapsedRealtime() - totalStartedAt}ms"
                        )

                        result
                    } catch (
                        exception: Exception
                    ) {
                        SaveCaptureResult.Failure(
                            listOf(
                                exception.message
                                    ?: "Could not save X post."
                            )
                        )
                    } finally {
                        temporaryDirectory
                            ?.deleteRecursively()
                    }
                }

                is XMediaDownloadResult.Failure -> {
                    SaveCaptureResult.Failure(
                        listOf(
                            downloadResult.error
                        )
                    )
                }
            }
        }

    private companion object {

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/120.0 Mobile Safari/537.36"
    }
}
