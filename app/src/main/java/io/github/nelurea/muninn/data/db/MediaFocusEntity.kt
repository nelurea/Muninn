package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_focus",
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
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("mediaId"),
        Index("attractionVocabularyId")
    ]
)
data class MediaFocusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val mediaId: Long,

    val attractionVocabularyId: Long?,

    val note: String?,

    val regionLeft: Float?,
    val regionTop: Float?,
    val regionRight: Float?,
    val regionBottom: Float?
)