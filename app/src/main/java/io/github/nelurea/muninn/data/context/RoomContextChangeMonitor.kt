package io.github.nelurea.muninn.data.context

import androidx.room.InvalidationTracker
import io.github.nelurea.muninn.data.db.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import java.io.IOException

sealed class ContextSyncStatus {
    data object Pending : ContextSyncStatus()
    data class Saved(val receipt: ContextCommitReceipt) : ContextSyncStatus()
    data class Blocked(val reason: String) : ContextSyncStatus()
}

/**
 * An opt-in observer. Call observe() from an explicitly managed, cancellable scope
 * only AFTER the user selected the Context folder and restore/reconcile is safe.
 * Its initial emission checks unsaved changes after process restart.
 *
 * Debouncing reduces redundant whole-snapshot writes. Monitoring is not wired to
 * the app lifecycle or settings yet; failed writes remain visibly Blocked rather
 * than being reported as Saved. Do not use this as a crash-proof event queue.
 */
class RoomContextChangeMonitor(
    private val database: AppDatabase,
    private val coordinator: ContextSyncCoordinator
) {
    private val mutableStatus = MutableStateFlow<ContextSyncStatus>(ContextSyncStatus.Pending)
    val status: StateFlow<ContextSyncStatus> = mutableStatus.asStateFlow()

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        database.invalidationTracker.createFlow(
            *RoomContextSnapshotExporter.TABLE_NAMES.toTypedArray(),
            emitInitialState = true
        ).onEach {
            mutableStatus.value = ContextSyncStatus.Pending
        }.debounce(1200L).collect {
            try {
                val result = coordinator.synchronize()
                mutableStatus.value = when (result) {
                    is ContextSyncResult.Committed -> ContextSyncStatus.Saved(result.receipt)
                    is ContextSyncResult.Unchanged -> ContextSyncStatus.Saved(result.receipt)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableStatus.value = ContextSyncStatus.Blocked(
                    error.message ?: "Context could not be saved"
                )
            }
        }
    }
}
