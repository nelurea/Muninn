package io.github.nelurea.muninn.discovery.pixiv

import android.webkit.CookieManager
import io.github.nelurea.muninn.discovery.DiscoveryPage
import io.github.nelurea.muninn.discovery.DiscoverySource
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PixivDiscoverySource(
    private val cookieManager: CookieManager =
        CookieManager.getInstance()
) : DiscoverySource {

    override val sourceId =
        DiscoverySourceId.PIXIV

    override suspend fun loadLatest(
        page: Int
    ): DiscoveryPage =
        withContext(
            Dispatchers.IO
        ) {
            require(
                page >= 1
            ) {
                "page must be >= 1"
            }

            val requestUrl =
                buildFollowingUrl(
                    page
                )

            val cookie =
                cookieManager.getCookie(
                    PIXIV_ORIGIN
                )

            if (
                cookie.isNullOrBlank()
            ) {
                throw PixivDiscoveryException(
                    "No authenticated Pixiv session is available."
                )
            }

            val connection =
                (
                        URL(
                            requestUrl
                        ).openConnection()
                                as HttpURLConnection
                        )

            try {
                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    CONNECT_TIMEOUT_MS

                connection.readTimeout =
                    READ_TIMEOUT_MS

                connection.instanceFollowRedirects =
                    true

                connection.setRequestProperty(
                    "Accept",
                    "application/json, text/plain, */*"
                )

                connection.setRequestProperty(
                    "Cookie",
                    cookie
                )

                connection.setRequestProperty(
                    "Referer",
                    "$PIXIV_ORIGIN/bookmark_new_illust.php"
                )

                connection.setRequestProperty(
                    "User-Agent",
                    USER_AGENT
                )

                val status =
                    connection.responseCode

                if (
                    status !in 200..299
                ) {
                    val errorBody =
                        connection.errorStream
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    throw PixivDiscoveryException(
                        buildString {
                            append(
                                "Pixiv Following request failed: HTTP "
                            )

                            append(
                                status
                            )

                            if (
                                !errorBody.isNullOrBlank()
                            ) {
                                append(
                                    " - "
                                )

                                append(
                                    errorBody.take(
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

                val response =
                    try {
                        PixivFollowingParser.parse(
                            json
                        )
                    } catch (
                        error: Exception
                    ) {
                        throw PixivDiscoveryException(
                            message =
                                "Failed to parse Pixiv Following response.",
                            cause =
                                error
                        )
                    }

                if (
                    response.error
                ) {
                    throw PixivDiscoveryException(
                        response.message
                            ?: "Pixiv returned an error."
                    )
                }

                val body =
                    response.body
                        ?: throw PixivDiscoveryException(
                            "Pixiv Following response has no body."
                        )

                val items =
                    PixivDiscoveryMapper.mapAll(
                        body.illusts
                            .orEmpty()
                    )

                DiscoveryPage(
                    items =
                        items,

                    page =
                        page,

                    hasNextPage =
                        body.lastPage != true &&
                                body.illusts
                                    .orEmpty()
                                    .isNotEmpty()
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun buildFollowingUrl(
        page: Int
    ): String {
        return buildString {
            append(
                PIXIV_ORIGIN
            )

            append(
                "/touch/ajax/follow/latest"
            )

            append(
                "?type=illusts"
            )

            append(
                "&p="
            )

            append(
                page
            )

            append(
                "&include_meta=1"
            )

            append(
                "&lang=ja"
            )
        }
    }

    private companion object {

        const val PIXIV_ORIGIN =
            "https://www.pixiv.net"

        const val CONNECT_TIMEOUT_MS =
            15_000

        const val READ_TIMEOUT_MS =
            20_000

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/120.0 Mobile Safari/537.36"
    }
}

class PixivDiscoveryException(
    message: String,
    cause: Throwable? = null
) : Exception(
    message,
    cause
)