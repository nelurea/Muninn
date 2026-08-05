package io.github.nelurea.muninn.debug.observation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object AssetInspector {

    fun inspect(
        context: Context,
        uri: Uri
    ): String {

        return buildString {

            appendLine("uri=$uri")

            appendLine(
                "displayName=${
                    queryString(
                        context,
                        uri,
                        OpenableColumns.DISPLAY_NAME
                    )
                }"
            )

            appendLine(
                "size=${
                    queryLong(
                        context,
                        uri,
                        OpenableColumns.SIZE
                    )
                }"
            )

            appendLine(
                "mimeType=${
                    context.contentResolver.getType(uri)
                }"
            )
        }
    }

    private fun queryString(
        context: Context,
        uri: Uri,
        column: String
    ): String? {

        context.contentResolver.query(
            uri,
            arrayOf(column),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }

        return null
    }

    private fun queryLong(
        context: Context,
        uri: Uri,
        column: String
    ): Long? {

        context.contentResolver.query(
            uri,
            arrayOf(column),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }

        return null
    }
}