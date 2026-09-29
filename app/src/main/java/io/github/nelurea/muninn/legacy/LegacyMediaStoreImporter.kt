package io.github.nelurea.muninn.legacy

import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.data.repository.LegacyMediaRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LegacyMediaStoreImporter(
    private val context: Context,
    private val repository: CapturedWorkRepository
) {

    suspend fun import(): ImportResult = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            return@withContext ImportResult(0, 0, completed = false)
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE
        )
        val selection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                    "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
            } else {
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            }
        val selectionArgs =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("Muninn/%", "Pictures/Muninn/%", "muninn_%.jpg")
            } else {
                arrayOf("muninn_%.jpg")
            }
        var scanned = 0
        val records = ArrayList<LegacyMediaRecord>()

        val cursor = try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} ASC"
            )
        } catch (_: SecurityException) {
            return@withContext ImportResult(scanned, 0, completed = false)
        } ?: return@withContext ImportResult(scanned, 0, completed = false)

        val result = cursor.use { mediaCursor ->
            val currentCandidateCount = mediaCursor.count
            if (
                preferences.getInt(KEY_CANDIDATE_COUNT, -1) ==
                    currentCandidateCount
            ) {
                return@use ImportResult(
                    scanned = currentCandidateCount,
                    imported = 0,
                    completed = true
                )
            }

            val idIndex = mediaCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameIndex =
                mediaCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateAddedIndex =
                mediaCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val mimeTypeIndex =
                mediaCursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (mediaCursor.moveToNext()) {
                scanned++
                val mediaId = mediaCursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )

                records += LegacyMediaRecord(
                    sourceId = "media-store-$mediaId",
                    localUri = uri.toString(),
                    capturedAt = timestamp(mediaCursor.getLong(dateAddedIndex)),
                    fileName = mediaCursor.getString(displayNameIndex),
                    mimeType = mediaCursor.getString(mimeTypeIndex) ?: "image/*"
                )
            }

            val imported = repository.importLegacyMediaBatch(records)
            preferences.edit()
                .putInt(KEY_CANDIDATE_COUNT, scanned)
                .apply()

            ImportResult(scanned, imported, completed = true)
        }

        result
    }

    fun requiredReadPermission(): String? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                Manifest.permission.READ_MEDIA_IMAGES

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                Manifest.permission.READ_EXTERNAL_STORAGE

            else -> null
        }

    fun hasReadPermission(): Boolean =
        requiredReadPermission()
            ?.let {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
            ?: true

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class ImportResult(
        val scanned: Int,
        val imported: Int,
        val completed: Boolean
    )

    private fun timestamp(dateAddedSeconds: Long): String =
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(
            Date(dateAddedSeconds * 1_000)
        )

    private companion object {
        const val PREFERENCES_NAME = "legacy_media_store_import"
        const val KEY_CANDIDATE_COUNT = "candidate_count"
    }
}
