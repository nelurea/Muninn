package io.github.nelurea.muninn.capture.discovery

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PixivUgoiraConversionResult(
    val sourceUrl: String,
    val outputFile: File,
    val frameCount: Int,
    val durationMs: Long
)

private data class PixivUgoiraFrame(
    val fileName: String,
    val delayMs: Long
)

private data class PixivUgoiraMetadata(
    val sourceUrl: String,
    val frames: List<PixivUgoiraFrame>
)

@UnstableApi
class PixivUgoiraConverter(
    context: Context,
    private val cookieManager: CookieManager =
        CookieManager.getInstance()
) {
    private val appContext =
        context.applicationContext

    suspend fun convert(
        artworkId: String,
        canonicalUrl: String,
        workingDirectory: File
    ): PixivUgoiraConversionResult =
        withContext(
            Dispatchers.IO
        ) {
            val startedAt =
                SystemClock.elapsedRealtime()

            val metadata =
                loadMetadata(
                    artworkId =
                        artworkId,
                    canonicalUrl =
                        canonicalUrl
                )

            val zipFile =
                File(
                    workingDirectory,
                    "source.zip"
                )

            downloadZip(
                sourceUrl =
                    metadata.sourceUrl,
                canonicalUrl =
                    canonicalUrl,
                destination =
                    zipFile
            )

            val framesDirectory =
                File(
                    workingDirectory,
                    "frames"
                )

            if (
                !framesDirectory.mkdirs()
            ) {
                throw IllegalStateException(
                    "Could not create ugoira frame directory."
                )
            }

            val frameFiles =
                extractFrames(
                    zipFile =
                        zipFile,
                    frames =
                        metadata.frames,
                    destinationDirectory =
                        framesDirectory
                )

            val outputFile =
                File(
                    workingDirectory,
                    "ugoira.mp4"
                )

            exportMp4(
                frameFiles =
                    frameFiles,
                frames =
                    metadata.frames,
                outputFile =
                    outputFile
            )

            if (
                !outputFile.exists() ||
                outputFile.length() <= 0L
            ) {
                throw IllegalStateException(
                    "Converted ugoira MP4 is empty."
                )
            }

            val durationMs =
                metadata.frames
                    .sumOf {
                        it.delayMs
                    }

            Log.i(
                LOG_TAG,
                "converted " +
                    "source=pixiv " +
                    "artworkId=$artworkId " +
                    "frames=${metadata.frames.size} " +
                    "durationMs=$durationMs " +
                    "outputBytes=${outputFile.length()} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
            )

            PixivUgoiraConversionResult(
                sourceUrl =
                    metadata.sourceUrl,
                outputFile =
                    outputFile,
                frameCount =
                    metadata.frames.size,
                durationMs =
                    durationMs
            )
        }

    private fun loadMetadata(
        artworkId: String,
        canonicalUrl: String
    ): PixivUgoiraMetadata {
        val response =
            requestJson(
                url =
                    "$PIXIV_ORIGIN/ajax/illust/$artworkId/ugoira_meta?lang=ja",
                referer =
                    canonicalUrl
            )

        if (
            response.optBoolean(
                "error",
                false
            )
        ) {
            throw IllegalStateException(
                response.optString(
                    "message",
                    "Pixiv ugoira metadata returned an error."
                )
            )
        }

        val body =
            response.optJSONObject(
                "body"
            )
                ?: throw IllegalStateException(
                    "Pixiv ugoira metadata has no body."
                )

        val sourceUrl =
            body.optNullableString(
                "originalSrc"
            )
                ?: body.optNullableString(
                    "src"
                )
                ?: throw IllegalStateException(
                    "Pixiv ugoira ZIP URL is missing."
                )

        val rawFrames =
            body.optJSONArray(
                "frames"
            )
                ?: throw IllegalStateException(
                    "Pixiv ugoira metadata has no frames."
                )

        val frames =
            buildList {
                for (
                    index in
                    0 until rawFrames.length()
                ) {
                    val frame =
                        rawFrames.optJSONObject(
                            index
                        )
                            ?: throw IllegalStateException(
                                "Invalid ugoira frame metadata at index $index."
                            )

                    val fileName =
                        frame.optNullableString(
                            "file"
                        )
                            ?: throw IllegalStateException(
                                "Ugoira frame file is missing at index $index."
                            )

                    val delayMs =
                        frame.optLong(
                            "delay",
                            -1L
                        )

                    if (
                        delayMs <= 0L
                    ) {
                        throw IllegalStateException(
                            "Invalid ugoira frame delay at index $index: $delayMs"
                        )
                    }

                    add(
                        PixivUgoiraFrame(
                            fileName =
                                fileName,
                            delayMs =
                                delayMs
                        )
                    )
                }
            }

        if (
            frames.isEmpty()
        ) {
            throw IllegalStateException(
                "Pixiv ugoira contains no frames."
            )
        }

        if (
            frames
                .map {
                    it.fileName
                }
                .distinct()
                .size !=
            frames.size
        ) {
            throw IllegalStateException(
                "Pixiv ugoira frame names are not unique."
            )
        }

        return PixivUgoiraMetadata(
            sourceUrl =
                sourceUrl,
            frames =
                frames
        )
    }

    private fun requestJson(
        url: String,
        referer: String
    ): JSONObject {
        val connection =
            URL(
                url
            ).openConnection()
                    as HttpURLConnection

        try {
            connection.requestMethod =
                "GET"

            connection.instanceFollowRedirects =
                true

            connection.connectTimeout =
                CONNECT_TIMEOUT_MS

            connection.readTimeout =
                READ_TIMEOUT_MS

            connection.setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
            )

            connection.setRequestProperty(
                "Referer",
                referer
            )

            connection.setRequestProperty(
                "User-Agent",
                USER_AGENT
            )

            cookieManager
                .getCookie(
                    PIXIV_ORIGIN
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

            val status =
                connection.responseCode

            if (
                status !in
                200..299
            ) {
                val errorText =
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }

                throw IllegalStateException(
                    buildString {
                        append(
                            "Pixiv ugoira metadata request failed: HTTP "
                        )
                        append(
                            status
                        )

                        if (
                            !errorText.isNullOrBlank()
                        ) {
                            append(
                                " - "
                            )
                            append(
                                errorText.take(
                                    300
                                )
                            )
                        }
                    }
                )
            }

            val json =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            return JSONObject(
                json
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadZip(
        sourceUrl: String,
        canonicalUrl: String,
        destination: File
    ) {
        val connection =
            URL(
                sourceUrl
            ).openConnection()
                    as HttpURLConnection

        try {
            connection.requestMethod =
                "GET"

            connection.instanceFollowRedirects =
                true

            connection.connectTimeout =
                CONNECT_TIMEOUT_MS

            connection.readTimeout =
                ZIP_READ_TIMEOUT_MS

            connection.setRequestProperty(
                "Referer",
                canonicalUrl
            )

            connection.setRequestProperty(
                "User-Agent",
                USER_AGENT
            )

            cookieManager
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

            val status =
                connection.responseCode

            if (
                status !in
                200..299
            ) {
                throw IllegalStateException(
                    "Pixiv ugoira ZIP download failed: HTTP $status"
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
                    "Downloaded Pixiv ugoira ZIP is empty."
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractFrames(
        zipFile: File,
        frames: List<PixivUgoiraFrame>,
        destinationDirectory: File
    ): List<File> {
        val targets =
            frames
                .mapIndexed {
                        index,
                        frame ->

                    val extension =
                        safeExtension(
                            frame.fileName
                        )

                    frame.fileName to
                        File(
                            destinationDirectory,
                            "frame-${index.toString().padStart(6, '0')}.$extension"
                        )
                }
                .toMap()

        ZipInputStream(
            zipFile
                .inputStream()
                .buffered()
        ).use {
                zip ->

            while (
                true
            ) {
                val entry =
                    zip.nextEntry
                        ?: break

                try {
                    if (
                        !entry.isDirectory
                    ) {
                        val target =
                            targets[
                                entry.name
                            ]

                        if (
                            target != null
                        ) {
                            target
                                .outputStream()
                                .use {
                                        output ->

                                    zip.copyTo(
                                        output
                                    )
                                }
                        }
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }

        return frames.map {
                frame ->

            val target =
                targets.getValue(
                    frame.fileName
                )

            if (
                !target.exists() ||
                target.length() <= 0L
            ) {
                throw IllegalStateException(
                    "Ugoira ZIP is missing frame: ${frame.fileName}"
                )
            }

            target
        }
    }

    private fun safeExtension(
        fileName: String
    ): String {
        val extension =
            fileName
                .substringAfterLast(
                    ".",
                    "jpg"
                )
                .lowercase()

        return extension
            .takeIf {
                it.matches(
                    Regex(
                        "[a-z0-9]{1,5}"
                    )
                )
            }
            ?: "jpg"
    }

    private suspend fun exportMp4(
        frameFiles: List<File>,
        frames: List<PixivUgoiraFrame>,
        outputFile: File
    ) {
        check(
            frameFiles.size ==
            frames.size
        ) {
            "Ugoira frame file count does not match metadata."
        }

        val editedItems =
            frameFiles
                .zip(
                    frames
                )
                .map {
                        pair ->

                    val file =
                        pair.first

                    val frame =
                        pair.second

                    val mediaItem =
                        MediaItem
                            .Builder()
                            .setUri(
                                Uri.fromFile(
                                    file
                                )
                            )
                            .setImageDurationMs(
                                frame.delayMs
                            )
                            .build()

                    EditedMediaItem
                        .Builder(
                            mediaItem
                        )
                        .setFrameRate(
                            TARGET_FRAME_RATE
                        )
                        .build()
                }

        val sequence =
            EditedMediaItemSequence
                .withVideoFrom(
                    editedItems
                )

        val composition =
            Composition
                .Builder(
                    sequence
                )
                .build()

        if (
            outputFile.exists()
        ) {
            check(
                outputFile.delete()
            ) {
                "Could not replace previous ugoira MP4."
            }
        }

        withContext(
            Dispatchers.Main.immediate
        ) {
            suspendCancellableCoroutine<Unit> {
                    continuation ->

                lateinit var transformer:
                    Transformer

                transformer =
                    Transformer
                        .Builder(
                            appContext
                        )
                        .setVideoMimeType(
                            MimeTypes.VIDEO_H264
                        )
                        .addListener(
                            object :
                                Transformer.Listener {

                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult
                                ) {
                                    if (
                                        continuation.isActive
                                    ) {
                                        continuation.resume(
                                            Unit
                                        )
                                    }
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    if (
                                        continuation.isActive
                                    ) {
                                        continuation
                                            .resumeWithException(
                                                exportException
                                            )
                                    }
                                }
                            }
                        )
                        .build()

                continuation
                    .invokeOnCancellation {
                        transformer.cancel()
                    }

                try {
                    transformer.start(
                        composition,
                        outputFile.absolutePath
                    )
                } catch (
                    exception: Exception
                ) {
                    if (
                        continuation.isActive
                    ) {
                        continuation
                            .resumeWithException(
                                exception
                            )
                    }
                }
            }
        }
    }

    private fun JSONObject
            .optNullableString(
        key: String
    ): String? {
        if (
            !has(
                key
            ) ||
            isNull(
                key
            )
        ) {
            return null
        }

        return optString(
            key
        )
            .takeIf {
                it.isNotBlank()
            }
    }

    private companion object {

        const val PIXIV_ORIGIN =
            "https://www.pixiv.net"

        const val CONNECT_TIMEOUT_MS =
            15_000

        const val READ_TIMEOUT_MS =
            20_000

        const val ZIP_READ_TIMEOUT_MS =
            60_000

        const val TARGET_FRAME_RATE =
            60

        const val LOG_TAG =
            "Muninn/Ugoira"

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) " +
                "AppleWebKit/537.36 " +
                "(KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }
}
