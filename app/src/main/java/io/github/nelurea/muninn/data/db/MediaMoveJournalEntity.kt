package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_move_journal",
    foreignKeys = [
        ForeignKey(
            entity = CapturedMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mediaId", unique = true)]
)
data class MediaMoveJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val sourceUri: String,
    val destinationRootUri: String?,
    val destinationUri: String? = null,
    val state: String = MediaMoveState.PENDING,
    val byteCount: Long? = null,
    val lastError: String? = null,
    val updatedAt: Long
)

object MediaMoveState {
    const val PENDING = "PENDING"
    const val COPYING = "COPYING"
    const val COPIED = "COPIED"
    const val DB_SWITCHED = "DB_SWITCHED"
    const val COMPLETED = "COMPLETED"
}
