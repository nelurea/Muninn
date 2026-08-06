package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_capture")
data class PendingCaptureEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sourceUrl: String,

    val imageIndex: Int?,

    val createdAt: Long
)