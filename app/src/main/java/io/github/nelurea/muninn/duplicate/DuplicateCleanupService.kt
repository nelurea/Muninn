package io.github.nelurea.muninn.duplicate

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.DuplicateCleanupJournalEntity
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DuplicateCleanupError {
    const val URI_STILL_REFERENCED = "URI_STILL_REFERENCED"
    const val UNSUPPORTED_URI = "UNSUPPORTED_URI"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val DELETE_FAILED = "DELETE_FAILED"
}

sealed interface DuplicateCleanupDeleteResult {
    data object Deleted : DuplicateCleanupDeleteResult
    data object Missing : DuplicateCleanupDeleteResult
    data class Failed(val error: String) : DuplicateCleanupDeleteResult
}

fun interface DuplicateCleanupDeleter {
    fun delete(targetUri: String): DuplicateCleanupDeleteResult
}

interface DuplicateCleanupPersistence {
    suspend fun pending(): List<DuplicateCleanupJournalEntity>
    suspend fun process(
        entry: DuplicateCleanupJournalEntity,
        now: Long,
        delete: (String) -> DuplicateCleanupDeleteResult
    )
}

class RoomDuplicateCleanupPersistence(
    private val database: AppDatabase
) : DuplicateCleanupPersistence {
    private val dao = database.duplicateNormalizationDao()

    override suspend fun pending() = dao.getPendingCleanup()

    override suspend fun process(
        entry: DuplicateCleanupJournalEntity,
        now: Long,
        delete: (String) -> DuplicateCleanupDeleteResult
    ) {
        database.withTransaction {
            val current = dao.getCleanupById(entry.id)
                ?.takeIf { it.state == io.github.nelurea.muninn.data.db.DuplicateCleanupState.PENDING }
                ?: return@withTransaction
            if (dao.countMediaUsingUri(current.targetUri) != 0) {
                dao.recordCleanupError(current.id, DuplicateCleanupError.URI_STILL_REFERENCED, now)
                return@withTransaction
            }
            when (val result = delete(current.targetUri)) {
                DuplicateCleanupDeleteResult.Deleted,
                DuplicateCleanupDeleteResult.Missing -> check(dao.markCleanupCompleted(current.id, now) == 1) {
                    "Duplicate cleanup completion CAS failed"
                }
                is DuplicateCleanupDeleteResult.Failed ->
                    dao.recordCleanupError(current.id, result.error, now)
            }
        }
    }
}

class DuplicateCleanupService(
    private val persistence: DuplicateCleanupPersistence,
    private val deleter: DuplicateCleanupDeleter,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun run() = withContext(ioDispatcher) {
        persistence.pending().forEach { entry ->
            try {
                persistence.process(entry, now(), deleter::delete)
            } catch (_: Exception) {
                // Each transaction records expected failures. An unexpected failure must not stop the batch.
            }
        }
    }
}

class AndroidDuplicateCleanupDeleter(context: Context) : DuplicateCleanupDeleter {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override fun delete(targetUri: String): DuplicateCleanupDeleteResult {
        val uri = runCatching { Uri.parse(targetUri) }.getOrNull()
            ?: return DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.UNSUPPORTED_URI)
        return try {
            when (uri.scheme?.lowercase()) {
                ContentResolver.SCHEME_CONTENT -> deleteContent(uri)
                ContentResolver.SCHEME_FILE -> deleteFile(File(requireNotNull(uri.path)))
                null -> if (targetUri.isBlank()) {
                    DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.UNSUPPORTED_URI)
                } else {
                    deleteFile(File(targetUri))
                }
                else -> DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.UNSUPPORTED_URI)
            }
        } catch (_: FileNotFoundException) {
            DuplicateCleanupDeleteResult.Missing
        } catch (_: SecurityException) {
            DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.PERMISSION_DENIED)
        } catch (_: Exception) {
            DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.DELETE_FAILED)
        }
    }

    private fun deleteContent(uri: Uri): DuplicateCleanupDeleteResult {
        val deleted = if (DocumentsContract.isDocumentUri(appContext, uri)) {
            DocumentsContract.deleteDocument(resolver, uri)
        } else {
            resolver.delete(uri, null, null) > 0
        }
        return if (deleted) DuplicateCleanupDeleteResult.Deleted
        else DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.DELETE_FAILED)
    }

    private fun deleteFile(file: File): DuplicateCleanupDeleteResult {
        if (!file.exists()) return DuplicateCleanupDeleteResult.Missing
        return if (file.delete()) DuplicateCleanupDeleteResult.Deleted
        else DuplicateCleanupDeleteResult.Failed(DuplicateCleanupError.DELETE_FAILED)
    }
}
