package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId")
    ]
)
data class ImageRecord(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val imageUri: String,

    val createdAt: Long = System.currentTimeMillis(),

    val sessionId: Long
)