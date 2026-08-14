package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captured_media",
    foreignKeys = [
        ForeignKey(
            entity = CapturedWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workId"),
        Index(
            value = ["workId", "mediaIndex"],
            unique = true
        )
    ]
)
data class CapturedMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val workId: Long,
    val mediaIndex: Int,

    val localUri: String,
    val sourceUrl: String,
    val mimeType: String,
    val fileName: String,

    val isHighlighted: Boolean
)
