package io.github.nelurea.muninn.capturepackage

import org.json.JSONObject

object CapturePackageParser {

    fun parse(json: String): CapturePackage {
        val root = JSONObject(json)

        val sourceJson = root.getJSONObject("source")
        val contentJson = root.getJSONObject("content")
        val authorJson = contentJson.getJSONObject("author")
        val mediaJson = root.getJSONArray("media")

        val media = buildList {
            for (index in 0 until mediaJson.length()) {
                val item = mediaJson.getJSONObject(index)

                add(
                    CapturePackageMedia(
                        index = item.getInt("index"),
                        sourceUrl = item.getString("sourceUrl"),
                        mimeType = item.getString("mimeType"),
                        fileName = item.getString("fileName")
                    )
                )
            }
        }

        val tagsJson = contentJson.getJSONArray("tags")

        val tags = buildList {
            for (index in 0 until tagsJson.length()) {
                add(tagsJson.getString(index))
            }
        }

        return CapturePackage(
            schemaVersion = root.getInt("schemaVersion"),
            source = CapturePackageSource(
                type = sourceJson.getString("type"),
                id = sourceJson.getString("id"),
                canonicalUrl = sourceJson.getString("canonicalUrl")
            ),
            capturedAt = root.getString("capturedAt"),
            content = CapturePackageContent(
                author = CapturePackageAuthor(
                    id = authorJson.getString("id"),
                    name = authorJson.getString("name")
                ),
                title =
                    if (contentJson.isNull("title")) {
                        null
                    } else {
                        contentJson.getString("title")
                    },
                caption = contentJson.getString("caption"),
                tags = tags
            ),
            media = media
        )
    }
}