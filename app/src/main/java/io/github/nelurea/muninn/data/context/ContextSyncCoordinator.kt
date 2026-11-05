package io.github.nelurea.muninn.data.context

import android.content.Context
import android.net.Uri
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.AppDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

sealed class ContextSyncResult {
    data class Committed(val receipt: ContextCommitReceipt) : ContextSyncResult()
    data class Unchanged(val receipt: ContextCommitReceipt) : ContextSyncResult()
}

/**
 * Fail-closed, explicit Context synchronization. No UI/startup listener is enabled
 * by constructing this class. A missing local checkpoint cannot replace an
 * existing external Context after uninstall/reinstall. A lost external generation,
 * a second writer, or missing durable identities also blocks automatic overwrite.
 *
 * Existing durable entities cannot be deleted by this path until a later slice
 * implements verified tombstones and restore/reconciliation. Relation edits remain
 * exportable. This is intentional conservative behavior, not a deletion mechanism.
 */
class ContextSyncCoordinator(
    private val documents: ContextDocumentStore,
    private val checkpoints: ContextSyncCheckpointStore,
    private val treeKey: String,
    private val snapshot: () -> ByteArray,
    private val validate: (ByteArray) -> Unit = { RoomContextSnapshotExporter.validate(it) },
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    suspend fun synchronize(): ContextSyncResult = processLock.withLock {
        withContext(Dispatchers.IO) {
            if (treeKey.isBlank()) throw IOException("Context tree is not selected")
            val previous = checkpoints.load(treeKey)
            val reader = VerifiedContextJournal(documents)
            val external = reader.readLatest()
            if (previous == null && external != null) {
                throw IOException("External Context exists without a local checkpoint; restore/reconcile first")
            }
            if (previous != null && external == null) {
                throw IOException("Previously committed external Context is missing")
            }
            if (previous != null && external != null && !matches(previous, external.receipt)) {
                throw IOException("External Context advanced or changed; reconcile first")
            }
            external?.let { validate(it.bytes) }
            val next = snapshot()
            validate(next)
            // A fresh/empty database cannot silently erase identities already saved.
            if (external != null && !stableIds(next).containsAll(stableIds(external.bytes))) {
                throw IOException("Context identities disappeared; refusing automatic replacement")
            }
            val hash = sha256(next)
            if (external != null && external.receipt.sha256 == hash &&
                external.receipt.length == next.size) {
                return@withContext ContextSyncResult.Unchanged(external.receipt)
            }
            // Defeat same-millisecond ordering ties in VerifiedContextJournal.readLatest().
            val committedAfter = external?.receipt?.committedAtMs ?: -1L
            if (committedAfter == Long.MAX_VALUE) throw IOException("Invalid Context commit clock")
            val timestamp = maxOf(nowMs(), committedAfter + 1L)
            val writer = VerifiedContextJournal(documents, clockMs = { timestamp })
            // A second writer must not have advanced the journal while exporting.
            val beforeWrite = reader.readLatest()
            if (!sameGeneration(external, beforeWrite)) {
                throw IOException("External Context changed during export")
            }
            val receipt = writer.save(next)
            val verified = reader.readLatest()
            if (verified == null || verified.receipt.generation != receipt.generation ||
                verified.receipt.sha256 != receipt.sha256 || !verified.bytes.contentEquals(next)) {
                throw IOException("New Context generation is not the latest verified generation")
            }
            if (!checkpoints.save(treeKey, ContextSyncCheckpoint(receipt.generation, receipt.sha256))) {
                throw IOException("External Context committed, but local acknowledgment failed; reconcile before retry")
            }
            ContextSyncResult.Committed(receipt)
        }
    }

    private fun matches(point: ContextSyncCheckpoint, receipt: ContextCommitReceipt): Boolean =
        point.generation == receipt.generation && point.sha256 == receipt.sha256

    private fun sameGeneration(a: RecoveredContextSnapshot?, b: RecoveredContextSnapshot?): Boolean =
        if (a == null || b == null) a == null && b == null
        else a.receipt.generation == b.receipt.generation && a.receipt.sha256 == b.receipt.sha256

    private fun stableIds(bytes: ByteArray): Set<String> {
        val table = JSONObject(bytes.toString(Charsets.UTF_8))
            .getJSONObject("tables").getJSONObject("durable_identities")
        val columns = table.getJSONArray("columns")
        var stableIndex = -1
        for (i in 0 until columns.length()) {
            if (columns.getString(i) == "stableId") stableIndex = i
        }
        if (stableIndex < 0) throw IOException("Missing stable identity column")
        val rows = table.getJSONArray("rows")
        val ids = HashSet<String>()
        for (i in 0 until rows.length()) {
            val value = rows.getJSONArray(i).getString(stableIndex)
            if (!value.startsWith("s:") || value.length <= 2 || !ids.add(value)) {
                throw IOException("Invalid or duplicate stable identity")
            }
        }
        return ids
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        // Single process-wide lock for all coordinator instances and Context folders.
        private val processLock = Mutex()

        /** Only a caller holding a user-selected, persistently granted SAF tree may enable sync. */
        fun forFolder(context: Context, selectedTreeUri: Uri): ContextSyncCoordinator {
            val application = context.applicationContext
            val database: AppDatabase = AppDatabaseProvider.get(application)
            return ContextSyncCoordinator(
                SafContextDocumentStore(application, selectedTreeUri),
                SharedPreferencesContextSyncCheckpointStore(application),
                selectedTreeUri.toString(),
                snapshot = { RoomContextSnapshotExporter(database).export() }
            )
        }
    }
}
