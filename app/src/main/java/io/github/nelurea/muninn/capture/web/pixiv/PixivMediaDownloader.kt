package io.github.nelurea.muninn.capture.web.pixiv

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed interface PixivMediaDownloadResult {

    data class Success(
        val files: List<File>
    ) : PixivMediaDownloadResult

    data class Failure(
        val error: String
    ) : PixivMediaDownloadResult
}

class PixivMediaDownloader(
    private val context: Context
) {

    suspend fun download(
        payload: PixivCapturePayload,
        userAgent: String
    ): PixivMediaDownloadResult =
        withContext<PixivMediaDownloadResult>(
            Dispatchers.IO
        ) {
            val directory =
                File(
                    context.cacheDir,
                    "pixiv_capture/${UUID.randomUUID()}"
                )

            if (!directory.mkdirs()) {
                PixivMediaDownloadResult.Failure(
                    error =
                        "Could not create temporary media directory"
                )
            } else {
                try {
                    val files =
                        payload.media.map { media ->

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

                    PixivMediaDownloadResult.Success(
                        files = files
                    )
                } catch (exception: Exception) {
                    directory.deleteRecursively()

                    PixivMediaDownloadResult.Failure(
                        error =
                            exception.message
                                ?: "Pixiv media download failed"
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
            URL(sourceUrl)
                .openConnection() as HttpURLConnection

        try {
            connection.instanceFollowRedirects =
                true

            connection.connectTimeout =
                15_000

            connection.readTimeout =
                30_000

            connection.setRequestProperty(
                "Referer",
                "https://www.pixiv.net/"
            )

            connection.setRequestProperty(
                "User-Agent",
                userAgent
            )

            CookieManager
                .getInstance()
                .getCookie(sourceUrl)
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { cookie ->
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

            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
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
}