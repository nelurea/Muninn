package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "captured_tags",
    primaryKeys = ["workId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CapturedWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workId")
    ]
)
data class CapturedTagEntity(
    val workId: Long,
    val position: Int,
    val tag: String
)