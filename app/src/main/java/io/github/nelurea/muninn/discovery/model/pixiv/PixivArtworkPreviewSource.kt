package io.github.nelurea.muninn.discovery.pixiv

import android.webkit.CookieManager
import io.github.nelurea.muninn.discovery.ArtworkPreviewSource
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PixivArtworkPreviewSource(
    private val cookieManager: CookieManager =
        CookieManager.getInstance()
) : ArtworkPreviewSource {

    override suspend fun load(
        item: DiscoveryItem
    ): ArtworkPreview =
        withContext(
            Dispatchers.IO
        ) {
            require(
                item.source ==
                        DiscoverySourceId.PIXIV
            ) {
                "PixivArtworkPreviewSource only supports Pixiv items."
            }

            val artworkId =
                item.sourceItemId

            val cookie =
                requireCookie()

            val detail =
                requestJson(
                    url =
                        "$PIXIV_ORIGIN/ajax/illust/$artworkId?lang=ja",
                    referer =
                        item.canonicalUrl,
                    cookie =
                        cookie
                )

            if (
                detail.optBoolean(
                    "error",
                    false
                )
            ) {
                throw PixivDiscoveryException(
                    detail.optString(
                        "message",
                        "Pixiv artwork detail returned an error."
                    )
                )
            }

            val body =
                detail.optJSONObject(
                    "body"
                )
                    ?: throw PixivDiscoveryException(
                        "Pixiv artwork detail has no body."
                    )

            val userId =
                body.optNullableString(
                    "userId"
                )
                    ?: item.creator
                        ?.sourceCreatorId

            val creatorAvatarUrl =
                body.optNullableString(
                    "profileImageUrl"
                )
                    ?: userId
                        ?.let {
                                id ->

                            loadCreatorAvatarUrl(
                                userId =
                                    id,
                                cookie =
                                    cookie
                            )
                        }

            val pageCount =
                body.optInt(
                    "pageCount",
                    1
                )
                    .coerceAtLeast(
                        1
                    )

            val media =
                if (
                    pageCount == 1
                ) {
                    buildSingleMedia(
                        body =
                            body
                    )
                } else {
                    loadPages(
                        artworkId =
                            artworkId,
                        canonicalUrl =
                            item.canonicalUrl,
                        cookie =
                            cookie
                    )
                }

            ArtworkPreview(
                source =
                    DiscoverySourceId.PIXIV,

                sourceItemId =
                    artworkId,

                canonicalUrl =
                    item.canonicalUrl,

                publishedAt =
                    body.optNullableString(
                        "createDate"
                    ),

                title =
                    body.optNullableString(
                        "illustTitle"
                    )
                        ?: body.optNullableString(
                            "title"
                        )
                        ?: item.title,

                caption =
                    body.optNullableString(
                        "illustComment"
                    )
                        ?: body.optNullableString(
                            "description"
                        ),

                creator =
                    DiscoveryCreator(
                        sourceCreatorId =
                            userId,

                        name =
                            body.optNullableString(
                                "userName"
                            )
                                ?: item.creator
                                    ?.name
                    ),

                creatorAvatarUrl =
                    creatorAvatarUrl,

                tags =
                    extractTags(
                        body
                    ),

                media =
                    media
            )
        }

    private fun extractTags(
        body: JSONObject
    ): List<String> {
        val rawTags =
            body
                .optJSONObject(
                    "tags"
                )
                ?.optJSONArray(
                    "tags"
                )
                ?: return emptyList()

        return buildList {
            for (
            index in
            0 until rawTags.length()
            ) {
                when (
                    val value =
                        rawTags.opt(
                            index
                        )
                ) {
                    is String -> {
                        value
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let(
                                ::add
                            )
                    }

                    is JSONObject -> {
                        value
                            .optNullableString(
                                "tag"
                            )
                            ?.let(
                                ::add
                            )
                    }
                }
            }
        }
    }

    private fun loadCreatorAvatarUrl(
        userId: String,
        cookie: String
    ): String? {
        return runCatching {
            val response =
                requestJson(
                    url =
                        "$PIXIV_ORIGIN/ajax/user/$userId?full=1&lang=ja",
                    referer =
                        "$PIXIV_ORIGIN/users/$userId",
                    cookie =
                        cookie
                )

            if (
                response.optBoolean(
                    "error",
                    false
                )
            ) {
                return@runCatching null
            }

            val body =
                response.optJSONObject(
                    "body"
                )
                    ?: return@runCatching null

            body.optNullableString(
                "imageBig"
            )
                ?: body.optNullableString(
                    "image"
                )
                ?: body.optNullableString(
                    "profileImageUrl"
                )
        }.getOrNull()
    }

    private fun buildSingleMedia(
        body: JSONObject
    ): List<ArtworkPreviewMedia> {
        val urls =
            body.optJSONObject(
                "urls"
            )
                ?: throw PixivDiscoveryException(
                    "Pixiv artwork detail has no image URLs."
                )

        val originalUrl =
            urls.optNullableString(
                "original"
            )
                ?: throw PixivDiscoveryException(
                    "Pixiv artwork original image URL is missing."
                )

        val previewUrl =
            urls.optNullableString(
                "regular"
            )
                ?: urls.optNullableString(
                    "small"
                )
                ?: originalUrl

        return listOf(
            ArtworkPreviewMedia(
                mediaIndex =
                    0,
                previewUrl =
                    previewUrl,
                originalUrl =
                    originalUrl
            )
        )
    }

    private fun loadPages(
        artworkId: String,
        canonicalUrl: String,
        cookie: String
    ): List<ArtworkPreviewMedia> {
        val pagesResponse =
            requestJson(
                url =
                    "$PIXIV_ORIGIN/ajax/illust/$artworkId/pages?lang=ja",
                referer =
                    canonicalUrl,
                cookie =
                    cookie
            )

        if (
            pagesResponse.optBoolean(
                "error",
                false
            )
        ) {
            throw PixivDiscoveryException(
                pagesResponse.optString(
                    "message",
                    "Pixiv artwork pages returned an error."
                )
            )
        }

        val pages =
            pagesResponse.optJSONArray(
                "body"
            )
                ?: throw PixivDiscoveryException(
                    "Pixiv artwork pages response has no body."
                )

        return buildList {
            for (
            index in
            0 until pages.length()
            ) {
                val page =
                    pages.optJSONObject(
                        index
                    ) ?: continue

                val urls =
                    page.optJSONObject(
                        "urls"
                    ) ?: continue

                val originalUrl =
                    urls.optNullableString(
                        "original"
                    ) ?: continue

                val previewUrl =
                    urls.optNullableString(
                        "regular"
                    )
                        ?: urls.optNullableString(
                            "small"
                        )
                        ?: originalUrl

                add(
                    ArtworkPreviewMedia(
                        mediaIndex =
                            index,
                        previewUrl =
                            previewUrl,
                        originalUrl =
                            originalUrl
                    )
                )
            }
        }
            .takeIf {
                it.isNotEmpty()
            }
            ?: throw PixivDiscoveryException(
                "Pixiv artwork pages contain no usable media."
            )
    }

    private fun requestJson(
        url: String,
        referer: String,
        cookie: String
    ): JSONObject {
        val connection =
            URL(
                url
            ).openConnection()
                    as HttpURLConnection

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
                referer
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
                            "Pixiv artwork preview request failed: HTTP "
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

            return JSONObject(
                json
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun requireCookie(): String {
        return cookieManager
            .getCookie(
                PIXIV_ORIGIN
            )
            ?.takeIf {
                it.isNotBlank()
            }
            ?: throw PixivDiscoveryException(
                "No authenticated Pixiv session is available."
            )
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

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/120.0 Mobile Safari/537.36"
    }
}