package io.github.nelurea.muninn.discovery.pixiv

import org.json.JSONArray
import org.json.JSONObject

object PixivSearchParser {

    fun parse(
        json: String
    ): PixivSearchResponse {
        val root =
            JSONObject(
                json
            )

        return PixivSearchResponse(
            error =
                root.optBoolean(
                    "error",
                    false
                ),

            message =
                root.optNullableString(
                    "message"
                ),

            body =
                root.optJSONObject(
                    "body"
                )?.let(
                    ::parseBody
                )
        )
    }

    private fun parseBody(
        body: JSONObject
    ): PixivSearchBody {
        return PixivSearchBody(
            illustManga =
                body.optJSONObject(
                    "illustManga"
                )?.let(
                    ::parseIllustManga
                )
        )
    }

    private fun parseIllustManga(
        illustManga: JSONObject
    ): PixivSearchIllustManga {
        return PixivSearchIllustManga(
            data =
                illustManga.optJSONArray(
                    "data"
                )?.let(
                    ::parseIllusts
                )
                    .orEmpty(),

            total =
                illustManga.optNullableInt(
                    "total"
                ),

            lastPage =
                illustManga.optNullableInt(
                    "lastPage"
                )
        )
    }

    private fun parseIllusts(
        array: JSONArray
    ): List<PixivSearchIllust> {
        return buildList {
            for (
            index in
            0 until array.length()
            ) {
                val item =
                    array.optJSONObject(
                        index
                    ) ?: continue

                add(
                    parseIllust(
                        item
                    )
                )
            }
        }
    }

    private fun parseIllust(
        item: JSONObject
    ): PixivSearchIllust {
        return PixivSearchIllust(
            id =
                item.optNullableString(
                    "id"
                ),

            title =
                item.optNullableString(
                    "title"
                ),

            url =
                item.optNullableString(
                    "url"
                ),

            userId =
                item.optNullableString(
                    "userId"
                ),

            userName =
                item.optNullableString(
                    "userName"
                ),

            pageCount =
                item.optNullableInt(
                    "pageCount"
                ),

            xRestrict =
                item.optNullableInt(
                    "xRestrict"
                ),

            width =
                item.optNullableInt(
                    "width"
                ),

            height =
                item.optNullableInt(
                    "height"
                ),

            isAdContainer =
                item.optBoolean(
                    "isAdContainer",
                    false
                )
        )
    }

    private fun JSONObject
            .optNullableString(
        key: String
    ): String? {
        if (
            !has(key) ||
            isNull(key)
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

    private fun JSONObject
            .optNullableInt(
        key: String
    ): Int? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return runCatching {
            getInt(
                key
            )
        }.getOrNull()
    }
}