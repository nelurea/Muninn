package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "save_event_media",
    foreignKeys = [
        ForeignKey(
            entity = SaveEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["saveEventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CapturedMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["capturedMediaId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("saveEventId"),
        Index("capturedMediaId")
    ]
)
data class SaveEventMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val saveEventId: Long,
    val capturedMediaId: Long? = null,
    val mediaIndex: Int?,
    val localUri: String?,
    val sourceUrl: String?,
    val mimeType: String?,
    val fileName: String?,
    val wasRequested: Boolean? = null,
    val wasHighlighted: Boolean? = null,
    val wasNewlyStored: Boolean? = null,
    val isLegacyBackfill: Boolean = false
)
