package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "resolved_capture",
    indices = [
        Index(
            value = ["pendingCaptureId"],
            unique = true
        )
    ]
)
data class ResolvedCaptureEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val pendingCaptureId: Long,

    val sourceType: String,
    val sourceId: String,
    val imageIndex: Int?,
    val createdAt: Long
)