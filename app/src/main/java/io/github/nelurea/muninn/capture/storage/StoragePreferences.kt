package io.github.nelurea.muninn.capture.storage

import android.content.Context

enum class MediaStorageMode {
    INTERNAL,
    EXTERNAL
}

class StoragePreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun getTreeUri(): String? =
        preferences
            .getString(
                KEY_TREE_URI,
                null
            )
            ?.takeIf {
                it.isNotBlank()
            }

    fun setTreeUri(
        uri: String
    ) {
        preferences
            .edit()
            .putString(
                KEY_TREE_URI,
                uri
            )
            .apply()
    }

    fun clearTreeUri() {
        preferences
            .edit()
            .remove(
                KEY_TREE_URI
            )
            .apply()
    }

    fun getMode(): MediaStorageMode {
        val storedMode =
            preferences
                .getString(
                    KEY_STORAGE_MODE,
                    null
                )

        return when (
            storedMode
        ) {
            MediaStorageMode.INTERNAL.name ->
                MediaStorageMode.INTERNAL

            MediaStorageMode.EXTERNAL.name ->
                MediaStorageMode.EXTERNAL

            else ->
                if (
                    getTreeUri() != null
                ) {
                    MediaStorageMode.EXTERNAL
                } else {
                    MediaStorageMode.INTERNAL
                }
        }
    }

    fun setMode(
        mode: MediaStorageMode
    ) {
        preferences
            .edit()
            .putString(
                KEY_STORAGE_MODE,
                mode.name
            )
            .apply()
    }

    private companion object {

        const val PREFERENCES_NAME =
            "muninn_storage"

        const val KEY_TREE_URI =
            "media_storage_tree_uri"

        const val KEY_STORAGE_MODE =
            "media_storage_mode"
    }
}