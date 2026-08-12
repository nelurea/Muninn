package io.github.nelurea.muninn.discovery.pixiv

import org.json.JSONArray
import org.json.JSONObject

object PixivBookmarksParser {

    fun parse(
        json: String
    ): PixivBookmarksResponse {
        val root =
            JSONObject(
                json
            )

        return PixivBookmarksResponse(
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
    ): PixivBookmarksBody {
        return PixivBookmarksBody(
            bookmarks =
                body.optJSONArray(
                    "bookmarks"
                )?.let(
                    ::parseBookmarks
                ),

            total =
                body.optFlexibleInt(
                    "total"
                ),

            lastPage =
                body.optFlexibleInt(
                    "lastPage"
                )
        )
    }

    private fun parseBookmarks(
        array: JSONArray
    ): List<PixivBookmarkIllust> {
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
                    parseBookmark(
                        item
                    )
                )
            }
        }
    }

    private fun parseBookmark(
        item: JSONObject
    ): PixivBookmarkIllust {
        return PixivBookmarkIllust(
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
                item.optFlexibleInt(
                    "page_count"
                ),

            x_restrict =
                item.optFlexibleInt(
                    "x_restrict"
                ),

            width =
                item.optFlexibleInt(
                    "width"
                ),

            height =
                item.optFlexibleInt(
                    "height"
                ),

            author_details =
                item.optJSONObject(
                    "author_details"
                )?.let(
                    ::parseAuthor
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
                val value =
                    array.optString(
                        index,
                        ""
                    )

                if (
                    value.isNotBlank()
                ) {
                    add(
                        value
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
            .optFlexibleInt(
        key: String
    ): Int? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return when (
            val value =
                opt(
                    key
                )
        ) {
            is Number ->
                value.toInt()

            is String ->
                value.toIntOrNull()

            else ->
                null
        }
    }
}