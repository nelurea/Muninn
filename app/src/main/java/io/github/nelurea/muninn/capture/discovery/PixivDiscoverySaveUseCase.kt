package io.github.nelurea.muninn.capture.discovery

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiscoveryArtworkSaveMode {
    ALL,
    SELECTED
}

interface DiscoveryArtworkSaveUseCase {

    val sourceId: DiscoverySourceId

    suspend fun save(
        preview: ArtworkPreview,
        selectedMediaIndices: Set<Int>,
        mode: DiscoveryArtworkSaveMode,
        discoveryMode: String?,
        discoveryQuery: String?
    ): SaveCaptureResult
}

class PixivDiscoverySaveUseCase(
    private val context: Context,
    private val saveCaptureUseCase: SaveCaptureUseCase
) : DiscoveryArtworkSaveUseCase {

    override val sourceId =
        DiscoverySourceId.PIXIV

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

            require(
                preview.source ==
                        DiscoverySourceId.PIXIV
            ) {
                "PixivDiscoverySaveUseCase only supports Pixiv previews."
            }

            val savePlan =
                buildDiscoveryArtworkSavePlan(
                    preview =
                        preview,
                    selectedMediaIndices =
                        selectedMediaIndices,
                    mode =
                        mode
                )

            val planItems =
                when (
                    savePlan
                ) {
                    is DiscoveryArtworkSavePlan.Ready ->
                        savePlan.items

                    is DiscoveryArtworkSavePlan.Failure ->
                        return@withContext SaveCaptureResult.Failure(
                            listOf(
                                savePlan.error
                            )
                        )
                }
            val requestedMediaIndices =
                planItems
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
                            "pixiv",
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
                "pixiv source=${preview.sourceItemId} " +
                    "prepare=${SystemClock.elapsedRealtime() - prepareStartedAt}ms"
            )

            val baseDraft =
                CaptureDraft(
                    sourceType = "pixiv",
                    sourceId = preview.sourceItemId,
                    canonicalUrl = preview.canonicalUrl,
                    capturedAt = currentTimestamp(),
                    publishedAt = preview.publishedAt,
                    discoveryMode = discoveryMode,
                    discoveryQuery = discoveryQuery,
                    authorId = preview.creator?.sourceCreatorId ?: "",
                    authorName = preview.creator?.name ?: "",
                    title = preview.title,
                    caption = preview.caption ?: "",
                    tags = preview.tags,
                    media = emptyList()
                )

            if (
                preparation.missingMediaIndices.isEmpty()
            ) {
                Log.d(
                    "Muninn/SavePerf",
                    "pixiv source=${preview.sourceItemId} " +
                        "alreadySaved=true " +
                        "total=${SystemClock.elapsedRealtime() - totalStartedAt}ms"
                )
                return@withContext saveCaptureUseCase.save(
                    draft = baseDraft,
                    requestedMediaIndices = requestedMediaIndices,
                    highlightedMediaIndices = selectedMediaIndices
                )
            }

            val missingPlanItems =
                planItems.filter {
                    it.mediaIndex in
                        preparation.missingMediaIndices
                }

            val temporaryDirectory =
                File(
                    context.cacheDir,
                    "pixiv_discovery_capture/${UUID.randomUUID()}"
                )

            if (
                !temporaryDirectory.mkdirs()
            ) {
                return@withContext SaveCaptureResult.Failure(
                    listOf(
                        "Could not create temporary media directory."
                    )
                )
            }

            try {
                val downloadStartedAt =
                    SystemClock.elapsedRealtime()

                val downloadSemaphore =
                    Semaphore(
                        permits =
                            MAX_PARALLEL_DOWNLOADS
                    )

                val mediaDrafts =
                    coroutineScope {
                        missingPlanItems
                            .map {
                                    planItem ->

                                async {
                                    downloadSemaphore.withPermit {

                        createMediaDraft(
                            preview =
                                preview,
                            planItem =
                                planItem,
                            temporaryDirectory =
                                temporaryDirectory
                        )
                                    }
                                }
                            }
                            .awaitAll()
                    }

                Log.d(
                    "Muninn/SavePerf",
                    "pixiv source=${preview.sourceItemId} " +
                        "download=${SystemClock.elapsedRealtime() - downloadStartedAt}ms " +
                        "pages=${mediaDrafts.size}"
                )

                val draft = baseDraft.copy(media = mediaDrafts)

                val persistStartedAt =
                    SystemClock.elapsedRealtime()

                val result =
                    saveCaptureUseCase.save(
                        draft = draft,
                        requestedMediaIndices = requestedMediaIndices,
                        highlightedMediaIndices = selectedMediaIndices
                    )

                Log.d(
                    "Muninn/SavePerf",
                    "pixiv source=${preview.sourceItemId} " +
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
                            ?: "Could not save Pixiv artwork."
                    )
                )
            } finally {
                temporaryDirectory
                    .deleteRecursively()
            }
        }

    private suspend fun createMediaDraft(
        preview: ArtworkPreview,
        planItem: DiscoveryArtworkSavePlanItem,
        temporaryDirectory: File
    ): CaptureMediaDraft {
        if (
            planItem.mimeType ==
            VIDEO_MP4_MIME_TYPE
        ) {
            val cachedUgoiraFile =
                planItem.playbackUri
                    ?.let(
                        Uri::parse
                    )
                    ?.takeIf {
                        it.scheme ==
                            "file"
                    }
                    ?.path
                    ?.let(
                        ::File
                    )
                    ?.takeIf {
                        it.exists() &&
                        it.length() > 0L
                    }

            if (
                cachedUgoiraFile != null
            ) {
                return CaptureMediaDraft(
                    mediaIndex =
                        planItem.mediaIndex,

                    sourceUrl =
                        planItem.originalUrl,

                    mimeType =
                        VIDEO_MP4_MIME_TYPE,

                    fileName =
                        "pixiv-${preview.sourceItemId}-p${planItem.mediaIndex}.mp4",

                    sourceFile =
                        cachedUgoiraFile,

                    isHighlighted =
                        planItem.isHighlighted
                )
            }
            val ugoiraDirectory =
                File(
                    temporaryDirectory,
                    "ugoira-${planItem.mediaIndex}"
                )

            if (
                !ugoiraDirectory.mkdirs()
            ) {
                throw IllegalStateException(
                    "Could not create temporary ugoira directory."
                )
            }

            val conversion =
                PixivUgoiraConverter(
                    context =
                        context
                ).convert(
                    artworkId =
                        preview.sourceItemId,
                    canonicalUrl =
                        preview.canonicalUrl,
                    workingDirectory =
                        ugoiraDirectory
                )

            return CaptureMediaDraft(
                mediaIndex =
                    planItem.mediaIndex,

                sourceUrl =
                    conversion.sourceUrl,

                mimeType =
                    VIDEO_MP4_MIME_TYPE,

                fileName =
                    "pixiv-${preview.sourceItemId}-p${planItem.mediaIndex}.mp4",

                sourceFile =
                    conversion.outputFile,

                isHighlighted =
                    planItem.isHighlighted
            )
        }

        val fileName =
            resolveFileName(
                sourceUrl =
                    planItem.originalUrl,
                mediaIndex =
                    planItem.mediaIndex
            )

        val destination =
            File(
                temporaryDirectory,
                fileName
            )

        downloadOne(
            sourceUrl =
                planItem.originalUrl,
            destination =
                destination
        )

        return CaptureMediaDraft(
            mediaIndex =
                planItem.mediaIndex,

            sourceUrl =
                planItem.originalUrl,

            mimeType =
                URLConnection
                    .guessContentTypeFromName(
                        fileName
                    )
                    ?: "application/octet-stream",

            fileName =
                fileName,

            sourceFile =
                destination,

            isHighlighted =
                planItem.isHighlighted
        )
    }
    private fun currentTimestamp(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone =
                TimeZone.getTimeZone(
                    "UTC"
                )
        }.format(
            Date()
        )
    }

    private fun downloadOne(
        sourceUrl: String,
        destination: File
    ) {
        val connection =
            URL(
                sourceUrl
            ).openConnection()
                    as HttpURLConnection

        try {
            connection.instanceFollowRedirects =
                true

            connection.connectTimeout =
                CONNECT_TIMEOUT_MS

            connection.readTimeout =
                READ_TIMEOUT_MS

            connection.setRequestProperty(
                "Referer",
                PIXIV_ORIGIN
            )

            connection.setRequestProperty(
                "User-Agent",
                USER_AGENT
            )

            CookieManager
                .getInstance()
                .getCookie(
                    sourceUrl
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                        cookie ->

                    connection.setRequestProperty(
                        "Cookie",
                        cookie
                    )
                }

            connection.connect()

            if (
                connection.responseCode !in
                200..299
            ) {
                throw IllegalStateException(
                    "HTTP ${connection.responseCode} for $sourceUrl"
                )
            }

            connection
                .inputStream
                .use {
                        input ->

                    destination
                        .outputStream()
                        .use {
                                output ->

                            input.copyTo(
                                output
                            )
                        }
                }

            if (
                !destination.exists() ||
                destination.length() <= 0L
            ) {
                throw IllegalStateException(
                    "Downloaded media file is empty: ${destination.name}"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveFileName(
        sourceUrl: String,
        mediaIndex: Int
    ): String {
        val rawName =
            runCatching {
                URL(
                    sourceUrl
                )
                    .path
                    .substringAfterLast(
                        "/"
                    )
            }
                .getOrNull()
                ?.substringBefore(
                    "?"
                )
                ?.takeIf {
                    it.isNotBlank()
                }

        return rawName
            ?: "pixiv-$mediaIndex.bin"
    }

    private companion object {

        const val VIDEO_MP4_MIME_TYPE =
            "video/mp4"

        const val PIXIV_ORIGIN =
            "https://www.pixiv.net/"

        const val MAX_PARALLEL_DOWNLOADS =
            3
        const val CONNECT_TIMEOUT_MS =
            15_000

        const val READ_TIMEOUT_MS =
            30_000

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/120.0 Mobile Safari/537.36"
    }
}

data class DiscoveryArtworkSavePlanItem(
    val mediaIndex: Int,
    val originalUrl: String,
    val isHighlighted: Boolean,
    val mimeType: String? = null,
    val playbackUri: String? = null
)

sealed interface DiscoveryArtworkSavePlan {

    data class Ready(
        val items: List<DiscoveryArtworkSavePlanItem>
    ) : DiscoveryArtworkSavePlan

    data class Failure(
        val error: String
    ) : DiscoveryArtworkSavePlan
}

internal fun buildDiscoveryArtworkSavePlan(
    preview: ArtworkPreview,
    selectedMediaIndices: Set<Int>,
    mode: DiscoveryArtworkSaveMode
): DiscoveryArtworkSavePlan {

    val mediaToSave =
        when (
            mode
        ) {
            DiscoveryArtworkSaveMode.ALL ->
                preview.media

            DiscoveryArtworkSaveMode.SELECTED ->
                preview.media.filter {
                    it.mediaIndex in
                            selectedMediaIndices
                }
        }

    if (
        mediaToSave.isEmpty()
    ) {
        return DiscoveryArtworkSavePlan.Failure(
            error =
                "No artwork pages were selected."
        )
    }

    return DiscoveryArtworkSavePlan.Ready(
        items =
            mediaToSave.map {
                    media ->

                DiscoveryArtworkSavePlanItem(
                    mediaIndex =
                        media.mediaIndex,
                    originalUrl =
                        media.originalUrl,
                    isHighlighted =
                        media.mediaIndex in
                                selectedMediaIndices,
                    mimeType =
                        media.mimeType,
                    playbackUri =
                        media.playbackUri
                )
            }
    )
}
