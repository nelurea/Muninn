package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resolved_capture")
data class ResolvedCaptureEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sourceType: String,

    val sourceId: String,

    val imageIndex: Int?,

    val createdAt: Long
)