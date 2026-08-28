package io.github.nelurea.muninn.capture.web.x

import org.json.JSONObject

sealed interface XCaptureParseResult {

    data class Success(
        val payload: XCapturePayload
    ) : XCaptureParseResult

    data class Failure(
        val error: String
    ) : XCaptureParseResult
}

object XCaptureParser {

    fun parse(
        rawMessage: String
    ): XCaptureParseResult {

        return try {
            val root =
                JSONObject(
                    rawMessage
                )

            if (
                root.optString(
                    "type"
                ) != "X_CAPTURE_RESULT"
            ) {
                return XCaptureParseResult.Failure(
                    "Unsupported X bridge message type"
                )
            }

            if (
                !root.optBoolean(
                    "ok"
                )
            ) {
                return XCaptureParseResult.Failure(
                    root.optString(
                        "error",
                        "X capture failed"
                    )
                )
            }

            val capturePackage =
                root.getJSONObject(
                    "capturePackage"
                )

            XCaptureParseResult.Success(
                parseCapturePackage(
                    capturePackage
                )
            )
        } catch (
            exception: Exception
        ) {
            XCaptureParseResult.Failure(
                exception.message
                    ?: "Could not parse X capture message"
            )
        }
    }

    fun parseCapturePackage(
        capturePackage: JSONObject
    ): XCapturePayload {

        val source =
            capturePackage
                .getJSONObject(
                    "source"
                )

        val content =
            capturePackage
                .getJSONObject(
                    "content"
                )

        val author =
            content
                .getJSONObject(
                    "author"
                )

        val tags =
            buildList {
                val tagsJson =
                    content.optJSONArray(
                        "tags"
                    )

                if (
                    tagsJson != null
                ) {
                    for (
                    index in
                    0 until tagsJson.length()
                    ) {
                        val tag =
                            tagsJson
                                .optString(
                                    index
                                )
                                .trim()

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

        val mediaJson =
            capturePackage
                .getJSONArray(
                    "media"
                )

        val media =
            buildList {
                for (
                index in
                0 until mediaJson.length()
                ) {
                    val item =
                        mediaJson
                            .getJSONObject(
                                index
                            )

                    add(
                        XCaptureMediaPayload(
                            mediaIndex =
                                item.getInt(
                                    "index"
                                ),

                            sourceUrl =
                                item.getString(
                                    "sourceUrl"
                                ),

                            mimeType =
                                item.optNullableString(
                                    "mimeType"
                                ),

                            fileName =
                                item.getString(
                                    "fileName"
                                ),

                            previewUrl =
                                item.optNullableString(
                                    "previewUrl"
                                )
                        )
                    )
                }
            }

        require(
            media.isNotEmpty()
        ) {
            "X capture does not contain supported media"
        }

        return XCapturePayload(
            sourceType =
                source.getString(
                    "type"
                ),

            sourceId =
                source.getString(
                    "id"
                ),

            canonicalUrl =
                source.getString(
                    "canonicalUrl"
                ),

            capturedAt =
                capturePackage.getString(
                    "capturedAt"
                ),

            publishedAt =
                capturePackage.optNullableString(
                    "publishedAt"
                ),

            authorId =
                author.optNullableString(
                    "id"
                ),

            authorName =
                author.optNullableString(
                    "name"
                ),

            authorHandle =
                author.optNullableString(
                    "handle"
                ),

            title =
                content.optNullableString(
                    "title"
                ),

            caption =
                content.optNullableString(
                    "caption"
                ),

            tags =
                tags,

            media =
                media
        )
    }

    private fun JSONObject.optNullableString(
        name: String
    ): String? {
        if (
            !has(
                name
            ) ||
            isNull(
                name
            )
        ) {
            return null
        }

        return getString(
            name
        )
            .takeIf {
                it.isNotBlank()
            }
    }
}
