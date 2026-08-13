package io.github.nelurea.muninn.discovery.pixiv

import android.net.Uri
import android.webkit.CookieManager
import io.github.nelurea.muninn.discovery.DiscoveryPage
import io.github.nelurea.muninn.discovery.DiscoverySource
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PixivDiscoverySource(
    private val cookieManager: CookieManager =
        CookieManager.getInstance(),
    private val identityProvider:
    PixivAccountIdentityProvider =
        PixivAccountIdentityProvider(
            cookieManager
        )
) : DiscoverySource {

    override val sourceId =
        DiscoverySourceId.PIXIV

    override suspend fun load(
        mode: DiscoveryMode,
        page: Int,
        query: String?
    ): DiscoveryPage {
        return when (
            mode
        ) {
            DiscoveryMode.LATEST -> {
                loadFollowing(
                    page =
                        page
                )
            }

            DiscoveryMode.BOOKMARKS -> {
                loadBookmarks(
                    page =
                        page
                )
            }

            DiscoveryMode.SEARCH -> {
                val searchQuery =
                    query
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: throw PixivDiscoveryException(
                            "Search query must not be blank."
                        )

                loadSearch(
                    query =
                        searchQuery,
                    page =
                        page
                )
            }
        }
    }

    private suspend fun loadFollowing(
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
                    page =
                        page
                )

            val cookie =
                requireCookie()

            val connection =
                URL(
                    requestUrl
                ).openConnection()
                        as HttpURLConnection

            try {
                configureConnection(
                    connection =
                        connection,
                    cookie =
                        cookie,
                    referer =
                        "$PIXIV_ORIGIN/bookmark_new_illust.php"
                )

                val status =
                    connection.responseCode

                if (
                    status !in 200..299
                ) {
                    throwRequestError(
                        connection =
                            connection,
                        label =
                            "Pixiv Following",
                        status =
                            status
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

    private suspend fun loadBookmarks(
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

            val userId =
                identityProvider
                    .getLoggedInUserId()
                    ?: throw PixivDiscoveryException(
                        "Could not determine the logged-in Pixiv user."
                    )

            val cookie =
                requireCookie()

            val requestUrl =
                buildBookmarksUrl(
                    userId =
                        userId,
                    page =
                        page
                )

            val connection =
                URL(
                    requestUrl
                ).openConnection()
                        as HttpURLConnection

            try {
                configureConnection(
                    connection =
                        connection,
                    cookie =
                        cookie,
                    referer =
                        "$PIXIV_ORIGIN/users/$userId/bookmarks/artworks"
                )

                val status =
                    connection.responseCode

                if (
                    status !in 200..299
                ) {
                    throwRequestError(
                        connection =
                            connection,
                        label =
                            "Pixiv Bookmarks",
                        status =
                            status
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
                        PixivBookmarksParser.parse(
                            json
                        )
                    } catch (
                        error: Exception
                    ) {
                        throw PixivDiscoveryException(
                            message =
                                "Failed to parse Pixiv Bookmarks response.",
                            cause =
                                error
                        )
                    }

                if (
                    response.error
                ) {
                    throw PixivDiscoveryException(
                        response.message
                            ?: "Pixiv returned a Bookmarks error."
                    )
                }

                val body =
                    response.body
                        ?: throw PixivDiscoveryException(
                            "Pixiv Bookmarks response has no body."
                        )

                val items =
                    PixivBookmarksMapper.mapAll(
                        body.bookmarks
                            .orEmpty()
                    )

                DiscoveryPage(
                    items =
                        items,
                    page =
                        page,
                    hasNextPage =
                        body.lastPage
                            ?.let {
                                    lastPage ->

                                page < lastPage
                            }
                            ?: body.bookmarks
                                .orEmpty()
                                .isNotEmpty()
                )
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun loadSearch(
        query: String,
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

            val cookie =
                requireCookie()

            val requestUrl =
                buildSearchUrl(
                    query =
                        query,
                    page =
                        page
                )

            val connection =
                URL(
                    requestUrl
                ).openConnection()
                        as HttpURLConnection

            try {
                configureConnection(
                    connection =
                        connection,
                    cookie =
                        cookie,
                    referer =
                        buildSearchReferer(
                            query =
                                query
                        )
                )

                val status =
                    connection.responseCode

                if (
                    status !in 200..299
                ) {
                    throwRequestError(
                        connection =
                            connection,
                        label =
                            "Pixiv Search",
                        status =
                            status
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
                        PixivSearchParser.parse(
                            json
                        )
                    } catch (
                        error: Exception
                    ) {
                        throw PixivDiscoveryException(
                            message =
                                "Failed to parse Pixiv Search response.",
                            cause =
                                error
                        )
                    }

                if (
                    response.error
                ) {
                    throw PixivDiscoveryException(
                        response.message
                            ?: "Pixiv returned a Search error."
                    )
                }

                val illustManga =
                    response.body
                        ?.illustManga
                        ?: throw PixivDiscoveryException(
                            "Pixiv Search response has no illustration data."
                        )

                val items =
                    PixivSearchMapper.mapAll(
                        illustManga.data
                    )

                DiscoveryPage(
                    items =
                        items,
                    page =
                        page,
                    hasNextPage =
                        illustManga.lastPage
                            ?.let {
                                    lastPage ->

                                page < lastPage
                            }
                            ?: items.isNotEmpty()
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun requireCookie(): String {
        return cookieManager.getCookie(
            PIXIV_ORIGIN
        )
            ?.takeIf {
                it.isNotBlank()
            }
            ?: throw PixivDiscoveryException(
                "No authenticated Pixiv session is available."
            )
    }

    private fun configureConnection(
        connection: HttpURLConnection,
        cookie: String,
        referer: String
    ) {
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
            referer
        )

        connection.setRequestProperty(
            "User-Agent",
            USER_AGENT
        )
    }

    private fun throwRequestError(
        connection: HttpURLConnection,
        label: String,
        status: Int
    ): Nothing {
        val errorBody =
            connection.errorStream
                ?.bufferedReader()
                ?.use {
                    it.readText()
                }

        throw PixivDiscoveryException(
            buildString {
                append(
                    label
                )

                append(
                    " request failed: HTTP "
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

    private fun buildBookmarksUrl(
        userId: String,
        page: Int
    ): String {
        return buildString {
            append(
                PIXIV_ORIGIN
            )

            append(
                "/touch/ajax/user/bookmarks"
            )

            append(
                "?id="
            )

            append(
                userId
            )

            append(
                "&type=illust"
            )

            append(
                "&rest=show"
            )

            append(
                "&p="
            )

            append(
                page
            )

            append(
                "&order=desc"
            )

            append(
                "&mode=all"
            )

            append(
                "&lang=ja"
            )
        }
    }

    private fun buildSearchUrl(
        query: String,
        page: Int
    ): String {
        val encodedQuery =
            Uri.encode(
                query
            )

        return buildString {
            append(
                PIXIV_ORIGIN
            )

            append(
                "/ajax/search/artworks/"
            )

            append(
                encodedQuery
            )

            append(
                "?order=date_d"
            )

            append(
                "&mode=all"
            )

            append(
                "&p="
            )

            append(
                page
            )

            append(
                "&ai_type=0"
            )

            append(
                "&csw=1"
            )

            append(
                "&s_mode=s_tag"
            )

            append(
                "&ratio="
            )

            append(
                "&lang=ja"
            )
        }
    }

    private fun buildSearchReferer(
        query: String
    ): String {
        return buildString {
            append(
                PIXIV_ORIGIN
            )

            append(
                "/search?q="
            )

            append(
                Uri.encode(
                    query
                )
            )

            append(
                "&s_mode=tag"
            )

            append(
                "&type=artwork"
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