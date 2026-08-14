package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "captured_media_attractions",
    primaryKeys = [
        "mediaId",
        "attractionVocabularyId"
    ],
    foreignKeys = [
        ForeignKey(
            entity = CapturedMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
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
        Index("mediaId"),
        Index("attractionVocabularyId")
    ]
)
data class CapturedMediaAttractionEntity(
    val mediaId: Long,
    val attractionVocabularyId: Long
)