package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_event")
data class CaptureEventEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sourceType: String,
    val sourceId: String,
    val imageIndex: Int?,

    val capturedAt: Long
)