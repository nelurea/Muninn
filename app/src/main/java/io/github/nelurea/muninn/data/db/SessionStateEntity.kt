package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "session_states",
    primaryKeys = [
        "sessionId",
        "stateVocabularyId"
    ],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StateVocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["stateVocabularyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("stateVocabularyId")
    ]
)
data class SessionStateEntity(
    val sessionId: Long,
    val stateVocabularyId: Long
)
