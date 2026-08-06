package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "acquisition_queue")
data class AcquisitionQueueEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val resolvedCaptureId: Long,

    val status: String,

    val createdAt: Long
)