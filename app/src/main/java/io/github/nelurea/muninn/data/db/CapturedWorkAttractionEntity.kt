package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "captured_work_attractions",
    primaryKeys = [
        "workId",
        "attractionVocabularyId"
    ],
    foreignKeys = [
        ForeignKey(
            entity = CapturedWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AttractionVocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["attractionVocabularyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workId"),
        Index("attractionVocabularyId")
    ]
)
data class CapturedWorkAttractionEntity(
    val workId: Long,
    val attractionVocabularyId: Long
)