package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "duplicate_cleanup_journal",
    foreignKeys = [
        ForeignKey(
            entity = DuplicateNormalizationJournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["normalizationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("normalizationId"),
        Index(value = ["normalizationId", "targetUri"], unique = true),
        Index("state")
    ]
)
data class DuplicateCleanupJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizationId: Long,
    val capturedMediaId: Long? = null,
    val targetUri: String,
    val state: String = DuplicateCleanupState.PENDING,
    val lastError: String? = null,
    val updatedAt: Long
)

object DuplicateCleanupState {
    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
}
