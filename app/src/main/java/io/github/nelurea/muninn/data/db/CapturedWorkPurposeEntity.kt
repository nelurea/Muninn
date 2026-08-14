package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "captured_work_purposes",
    primaryKeys = [
        "workId",
        "purposeVocabularyId"
    ],
    foreignKeys = [
        ForeignKey(
            entity = CapturedWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurposeVocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["purposeVocabularyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workId"),
        Index("purposeVocabularyId")
    ]
)
data class CapturedWorkPurposeEntity(
    val workId: Long,
    val purposeVocabularyId: Long
)
