package io.github.nelurea.muninn.capture.web.pixiv

import org.json.JSONObject

sealed interface PixivCaptureParseResult {

    data class Success(
        val payload: PixivCapturePayload
    ) : PixivCaptureParseResult

    data class Failure(
        val error: String
    ) : PixivCaptureParseResult
}

object PixivCaptureParser {

    fun parse(
        rawMessage: String
    ): PixivCaptureParseResult {

        return try {
            val root =
                JSONObject(rawMessage)

            if (
                root.optString("type") !=
                "PIXIV_CAPTURE_PROBE_RESULT"
            ) {
                return PixivCaptureParseResult.Failure(
                    "Unsupported Pixiv bridge message type"
                )
            }

            if (!root.optBoolean("ok")) {
                return PixivCaptureParseResult.Failure(
                    root.optString(
                        "error",
                        "Pixiv capture failed"
                    )
                )
            }

            val capturePackage =
                root.getJSONObject(
                    "capturePackage"
                )

            val source =
                capturePackage.getJSONObject(
                    "source"
                )

            val content =
                capturePackage.getJSONObject(
                    "content"
                )

            val author =
                content.getJSONObject(
                    "author"
                )

            val tagsJson =
                content.getJSONArray(
                    "tags"
                )

            val tags =
                buildList {
                    for (
                    index in
                    0 until tagsJson.length()
                    ) {
                        add(
                            tagsJson.getString(
                                index
                            )
                        )
                    }
                }

            val mediaJson =
                capturePackage.getJSONArray(
                    "media"
                )

            val media =
                buildList {
                    for (
                    index in
                    0 until mediaJson.length()
                    ) {
                        val item =
                            mediaJson.getJSONObject(
                                index
                            )

                        add(
                            PixivCaptureMediaPayload(
                                mediaIndex =
                                    item.getInt(
                                        "index"
                                    ),

                                sourceUrl =
                                    item.getString(
                                        "sourceUrl"
                                    ),

                                mimeType =
                                    item.optString(
                                        "mimeType"
                                    ).takeIf {
                                        it.isNotBlank() &&
                                                it != "null"
                                    },

                                fileName =
                                    item.getString(
                                        "fileName"
                                    )
                            )
                        )
                    }
                }

            val payload =
                PixivCapturePayload(
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

                    authorId =
                        author.optString(
                            "id"
                        ),

                    authorName =
                        author.optString(
                            "name"
                        ),

                    title =
                        content.optString(
                            "title"
                        ).takeIf {
                            it.isNotBlank() &&
                                    it != "null"
                        },

                    caption =
                        content.optString(
                            "caption"
                        ),

                    tags =
                        tags,

                    media =
                        media
                )

            PixivCaptureParseResult.Success(
                payload
            )
        } catch (exception: Exception) {
            PixivCaptureParseResult.Failure(
                exception.message
                    ?: "Could not parse Pixiv capture message"
            )
        }
    }
}