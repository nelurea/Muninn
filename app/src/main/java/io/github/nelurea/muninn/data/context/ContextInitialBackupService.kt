package io.github.nelurea.muninn.data.context

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.AppDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * An explicit initial export, NOT an automatic sync or a restore operation.
 *
 * An existing committed generation takes precedence over Room. Refuse to replace
 * it until a later slice implements safe reconciliation/restoration. This prevents
 * a fresh app with empty Room from shadowing the user's older external Context.
 */
class ContextInitialBackupService(
    private val database: AppDatabase,
    private val documents: ContextDocumentStore
) {
    suspend fun backupOnce(): ContextCommitReceipt = lock.withLock {
        withContext(Dispatchers.IO) {
            val journal = VerifiedContextJournal(documents)
            if (journal.readLatest() != null) {
                throw IOException("An external Context snapshot already exists; restore/reconcile first")
            }
            val bytes = RoomContextSnapshotExporter(database).export()
            journal.save(bytes)
        }
    }

    companion object {
        private val lock = Mutex()

        /** The caller must supply a user-chosen tree with persisted read/write access. */
        fun forFolder(context: Context, selectedTreeUri: Uri): ContextInitialBackupService =
            ContextInitialBackupService(
                AppDatabaseProvider.get(context.applicationContext),
                SafContextDocumentStore(context.applicationContext, selectedTreeUri)
            )
    }
}
