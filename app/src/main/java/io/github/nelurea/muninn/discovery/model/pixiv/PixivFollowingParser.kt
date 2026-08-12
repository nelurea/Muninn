package io.github.nelurea.muninn.discovery.pixiv

import org.json.JSONArray
import org.json.JSONObject

object PixivFollowingParser {

    fun parse(
        json: String
    ): PixivFollowingResponse {
        val root =
            JSONObject(
                json
            )

        return PixivFollowingResponse(
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
    ): PixivFollowingBody {
        return PixivFollowingBody(
            illusts =
                body.optJSONArray(
                    "illusts"
                )?.let(
                    ::parseIllusts
                ),

            total =
                body.optNullableInt(
                    "total"
                ),

            lastPage =
                body.optNullableBoolean(
                    "lastPage"
                )
        )
    }

    private fun parseIllusts(
        array: JSONArray
    ): List<PixivFollowingIllust> {
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
    ): PixivFollowingIllust {
        return PixivFollowingIllust(
            id =
                item.optNullableString(
                    "id"
                ),

            url =
                item.optNullableString(
                    "url"
                ),

            url_s =
                item.optNullableString(
                    "url_s"
                ),

            url_sm =
                item.optNullableString(
                    "url_sm"
                ),

            url_w =
                item.optNullableString(
                    "url_w"
                ),

            title =
                item.optNullableString(
                    "title"
                ),

            alt =
                item.optNullableString(
                    "alt"
                ),

            tags =
                item.optJSONArray(
                    "tags"
                )?.let(
                    ::parseTags
                ),

            page_count =
                item.optNullableInt(
                    "page_count"
                ),

            x_restrict =
                item.optNullableInt(
                    "x_restrict"
                ),

            width =
                item.optNullableInt(
                    "width"
                ),

            height =
                item.optNullableInt(
                    "height"
                ),

            author_details =
                item.optJSONObject(
                    "author_details"
                )?.let(
                    ::parseAuthor
                ),

            is_ad_container =
                item.optNullableInt(
                    "is_ad_container"
                )
        )
    }

    private fun parseAuthor(
        author: JSONObject
    ): PixivFollowingAuthor {
        return PixivFollowingAuthor(
            user_id =
                author.optNullableString(
                    "user_id"
                ),

            user_name =
                author.optNullableString(
                    "user_name"
                ),

            user_account =
                author.optNullableString(
                    "user_account"
                )
        )
    }

    private fun parseTags(
        array: JSONArray
    ): List<String> {
        return buildList {
            for (
            index in
            0 until array.length()
            ) {
                val tag =
                    array.optString(
                        index,
                        ""
                    )

                if (
                    tag.isNotBlank()
                ) {
                    add(
                        tag
                    )
                }
            }
        }
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

    private fun JSONObject
            .optNullableBoolean(
        key: String
    ): Boolean? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return runCatching {
            getBoolean(
                key
            )
        }.getOrNull()
    }
}