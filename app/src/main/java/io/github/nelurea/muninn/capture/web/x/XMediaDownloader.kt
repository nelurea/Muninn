package io.github.nelurea.muninn.capture.web.x

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed interface XMediaDownloadResult {

    data class Success(
        val files: List<File>
    ) : XMediaDownloadResult

    data class Failure(
        val error: String
    ) : XMediaDownloadResult
}

class XMediaDownloader(
    private val context: Context
) {

    suspend fun download(
        payload: XCapturePayload,
        userAgent: String
    ): XMediaDownloadResult =
        withContext<XMediaDownloadResult>(
            Dispatchers.IO
        ) {
            val directory =
                File(
                    context.cacheDir,
                    "x_capture/${UUID.randomUUID()}"
                )

            if (
                !directory.mkdirs()
            ) {
                XMediaDownloadResult.Failure(
                    error =
                        "Could not create temporary X media directory"
                )
            } else {
                try {
                    val files =
                        payload.media
                            .map {
                                    media ->

                                val destination =
                                    File(
                                        directory,
                                        media.fileName
                                    )

                                downloadOne(
                                    sourceUrl =
                                        media.sourceUrl,
                                    destination =
                                        destination,
                                    userAgent =
                                        userAgent
                                )

                                destination
                            }

                    XMediaDownloadResult.Success(
                        files =
                            files
                    )
                } catch (
                    exception: Exception
                ) {
                    directory
                        .deleteRecursively()

                    XMediaDownloadResult.Failure(
                        error =
                            exception.message
                                ?: "X media download failed"
                    )
                }
            }
        }

    private fun downloadOne(
        sourceUrl: String,
        destination: File,
        userAgent: String
    ) {
        val connection =
            URL(
                sourceUrl
            )
                .openConnection()
                    as HttpURLConnection

        try {
            connection
                .instanceFollowRedirects =
                true

            connection
                .connectTimeout =
                15_000

            connection
                .readTimeout =
                30_000

            connection.setRequestProperty(
                "Referer",
                "https://x.com/"
            )

            connection.setRequestProperty(
                "User-Agent",
                userAgent
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

                    connection
                        .setRequestProperty(
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

            val contentType =
                connection.contentType

            if (
                contentType != null &&
                !contentType.startsWith(
                    "image/",
                    ignoreCase = true
                ) &&
                !contentType.startsWith(
                    "video/",
                    ignoreCase = true
                )
            ) {
                throw IllegalStateException(
                    "Unexpected content type $contentType for $sourceUrl"
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
                    "Downloaded X media file is empty: ${destination.name}"
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
