package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** The current, user-authored purpose shared by all works in a session. */
@Entity(
    tableName = "session_purposes",
    primaryKeys = ["sessionId"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurposeVocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["purposeVocabularyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("purposeVocabularyId")]
)
data class SessionPurposeEntity(
    val sessionId: Long,
    val purposeVocabularyId: Long
)

/** Persisted last-used time plus links counted from current work/session assignments. */
data class PurposeUsageRow(
    val purposeId: Long,
    val useCount: Int,
    val lastUsedAt: Long
)
