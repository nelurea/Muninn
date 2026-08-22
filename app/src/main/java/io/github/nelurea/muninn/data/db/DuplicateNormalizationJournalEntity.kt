package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "duplicate_normalization_journal",
    indices = [
        Index(value = ["sourceType", "sourceId"], unique = true),
        Index("state")
    ]
)
data class DuplicateNormalizationJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceType: String,
    val sourceId: String,
    val state: String = DuplicateNormalizationState.PENDING,
    val verificationState: String = DuplicateVerificationState.UNKNOWN,
    val verificationDetails: String? = null,
    val canonicalWorkId: Long? = null,
    val planVersion: Int? = null,
    val planJson: String? = null,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

object DuplicateNormalizationState {
    const val PENDING = "PENDING"
    const val PLANNED = "PLANNED"
    const val COMPLETED = "COMPLETED"
}

object DuplicateVerificationState {
    const val UNKNOWN = "UNKNOWN"
    const val VERIFIED = "VERIFIED"
    const val REJECTED = "REJECTED"
}
