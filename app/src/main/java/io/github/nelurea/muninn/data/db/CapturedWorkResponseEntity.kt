package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "captured_work_responses",
    primaryKeys = [
        "workId",
        "responseVocabularyId"
    ],
    foreignKeys = [
        ForeignKey(
            entity = CapturedWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AestheticResponseVocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["responseVocabularyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workId"),
        Index("responseVocabularyId")
    ]
)
data class CapturedWorkResponseEntity(
    val workId: Long,
    val responseVocabularyId: Long
)