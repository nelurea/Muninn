package io.github.nelurea.muninn.capture.discovery

import android.content.Context
import android.webkit.CookieManager
import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DiscoveryArtworkSaveMode {
    ALL,
    SELECTED
}

class PixivDiscoverySaveUseCase(
    private val context: Context,
    private val saveCaptureUseCase: SaveCaptureUseCase
) {

    suspend fun save(
        preview: ArtworkPreview,
        selectedMediaIndices: Set<Int>,
        mode: DiscoveryArtworkSaveMode,
        discoveryMode: String?,
        discoveryQuery: String?
    ): SaveCaptureResult =
        withContext(
            Dispatchers.IO
        ) {
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
                return@withContext SaveCaptureResult.Failure(
                    listOf(
                        "No artwork pages were selected."
                    )
                )
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
                val mediaDrafts =
                    mediaToSave.map {
                            media ->

                        val fileName =
                            resolveFileName(
                                sourceUrl =
                                    media.originalUrl,
                                mediaIndex =
                                    media.mediaIndex
                            )

                        val destination =
                            File(
                                temporaryDirectory,
                                fileName
                            )

                        downloadOne(
                            sourceUrl =
                                media.originalUrl,
                            destination =
                                destination
                        )

                        CaptureMediaDraft(
                            mediaIndex =
                                media.mediaIndex,

                            sourceUrl =
                                media.originalUrl,

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
                                media.mediaIndex in
                                        selectedMediaIndices
                        )
                    }

                val draft =
                    CaptureDraft(
                        sourceType =
                            "pixiv",

                        sourceId =
                            preview.sourceItemId,

                        canonicalUrl =
                            preview.canonicalUrl,

                        capturedAt =
                            currentTimestamp(),

                        publishedAt =
                            preview.publishedAt,

                        discoveryMode =
                            discoveryMode,

                        discoveryQuery =
                            discoveryQuery,

                        authorId =
                            preview.creator
                                ?.sourceCreatorId
                                ?: "",

                        authorName =
                            preview.creator
                                ?.name
                                ?: "",

                        title =
                            preview.title,

                        caption =
                            preview.caption
                                ?: "",

                        tags =
                            preview.tags,

                        media =
                            mediaDrafts
                    )

                saveCaptureUseCase.save(
                    draft
                )
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
            ?: "pixiv-${mediaIndex}.bin"
    }

    private companion object {

        const val PIXIV_ORIGIN =
            "https://www.pixiv.net/"

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