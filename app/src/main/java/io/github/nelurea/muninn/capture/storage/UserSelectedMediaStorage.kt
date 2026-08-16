package io.github.nelurea.muninn.capture.storage

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.capture.model.CaptureMediaDraft
import java.io.File
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

    override suspend fun delete(
        localUris: List<String>
    ) {
        withContext(
            Dispatchers.IO
        ) {
            localUris.forEach {
                    rawUri ->

                val uri =
                    Uri.parse(
                        rawUri
                    )

                when (
                    uri.scheme
                ) {
                    "content" -> {
                        runCatching {
                            appContext
                                .contentResolver
                                .delete(
                                    uri,
                                    null,
                                    null
                                )
                        }
                    }

                    "file" -> {
                        uri.path
                            ?.let(
                                ::File
                            )
                            ?.delete()
                    }
                }
            }

            localUris
                .mapNotNull {
                        rawUri ->

                    val uri =
                        Uri.parse(
                            rawUri
                        )

                    if (
                        uri.scheme ==
                        "file"
                    ) {
                        uri.path
                            ?.let(
                                ::File
                            )
                            ?.parentFile
                    } else {
                        null
                    }
                }
                .distinct()
                .forEach {
                        directory ->

                    if (
                        directory.exists() &&
                        directory
                            .listFiles()
                            ?.isEmpty() ==
                        true
                    ) {
                        directory.delete()
                    }
                }
        }
    }
}