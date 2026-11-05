package io.github.nelurea.muninn.data.context

import android.content.Context
import java.io.IOException

/** Application-private acknowledgment, NOT the external Context source of truth. */
data class ContextSyncCheckpoint(
    val generation: String,
    val sha256: String
)

interface ContextSyncCheckpointStore {
    /** A different selected tree must not silently inherit another tree's checkpoint. */
    @Throws(IOException::class)
    fun load(treeKey: String): ContextSyncCheckpoint?

    /** Must be persisted synchronously; false means the write was not acknowledged. */
    @Throws(IOException::class)
    fun save(treeKey: String, value: ContextSyncCheckpoint): Boolean
}

/**
 * A lost preference (e.g., app-data deletion) never authorizes replacing an existing
 * external generation: ContextSyncCoordinator refuses that case explicitly.
 */
class SharedPreferencesContextSyncCheckpointStore(context: Context) : ContextSyncCheckpointStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "muninn_context_sync_checkpoint_v1", Context.MODE_PRIVATE
    )

    override fun load(treeKey: String): ContextSyncCheckpoint? {
        require(treeKey.isNotBlank())
        val boundTree = preferences.getString("tree", null) ?: return null
        if (boundTree != treeKey) {
            throw IOException("Context folder changed; restore or reconcile before synchronizing")
        }
        val generation = preferences.getString("generation", null)
            ?: throw IOException("Incomplete local Context checkpoint")
        val checksum = preferences.getString("sha256", null)
            ?: throw IOException("Incomplete local Context checkpoint")
        if (!GENERATION.matches(generation) || !CHECKSUM.matches(checksum)) {
            throw IOException("Invalid local Context checkpoint")
        }
        return ContextSyncCheckpoint(generation, checksum)
    }

    override fun save(treeKey: String, value: ContextSyncCheckpoint): Boolean {
        require(treeKey.isNotBlank())
        require(GENERATION.matches(value.generation) && CHECKSUM.matches(value.sha256))
        val previous = preferences.getString("tree", null)
        if (previous != null && previous != treeKey) {
            throw IOException("Refusing to change Context checkpoint folder")
        }
        return preferences.edit()
            .putString("tree", treeKey)
            .putString("generation", value.generation)
            .putString("sha256", value.sha256)
            .commit()
    }

    private companion object {
        val GENERATION = Regex("[0-9a-f]{32}")
        val CHECKSUM = Regex("[0-9a-f]{64}")
    }
}
