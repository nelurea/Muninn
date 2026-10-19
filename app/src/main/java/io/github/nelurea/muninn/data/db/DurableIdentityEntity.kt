package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index

/** Stable identities for restoring relationships after Room row IDs change.
 *
 * A side table avoids rebuilding existing tables and touching their foreign keys.
 * [rowId] is local to the current Room database; [stableId] is exportable.
 */
@Entity(
    tableName = "durable_identities",
    primaryKeys = ["entityType", "rowId"],
    indices = [Index(value = ["stableId"], unique = true)]
)
data class DurableIdentityEntity(
    val entityType: String,
    val rowId: Long,
    val stableId: String
)

object DurableIdentityType {
    const val SESSION = "session"
    const val WORK = "work"
    const val MEDIA = "media"
    const val LEGACY_IMAGE = "legacy_image"
    const val FOCUS = "focus"
}
