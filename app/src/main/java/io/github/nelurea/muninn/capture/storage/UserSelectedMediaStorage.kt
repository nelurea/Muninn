package io.github.nelurea.muninn.capture.storage

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserSelectedMediaStorage(
    context: Context
) : MediaStorage {

    private val appContext =
        context.applicationContext

    private val preferences =
        StoragePreferences(
            appContext
        )

    override suspend fun store(
        media: List<CaptureMediaDraft>
    ): MediaStorageResult {

        return when (
            preferences.getMode()
        ) {
            MediaStorageMode.INTERNAL -> {
                InternalMediaStorage(
                    appContext
                ).store(
                    media
                )
            }

            MediaStorageMode.EXTERNAL -> {
                val treeUri =
                    preferences
                        .getTreeUri()
                        ?: return MediaStorageResult.Failure(
                            "External storage folder is not configured."
                        )

                SafMediaStorage(
                    context =
                        appContext,
                    treeUriString =
                        treeUri
                ).store(
                    media
                )
            }
        }
    }

    override suspend fun delete(localUris: List<String>) {
        withContext(Dispatchers.IO) {
            val internalCleanup = InternalMediaFileCleanup(appContext.filesDir)
            localUris.distinct().forEach { rawUri ->
                when (Uri.parse(rawUri).scheme) {
                    "content" -> runCatching {
                        appContext.contentResolver.delete(Uri.parse(rawUri), null, null)
                    }
                    "file" -> internalCleanup.delete(rawUri)
                }
            }
        }
    }
}
