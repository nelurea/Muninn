package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captured_works",
    indices = [
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class CapturedWorkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sourceType: String,
    val sourceId: String,
    val canonicalUrl: String,
    val capturedAt: String,

    val authorId: String,
    val authorName: String,
    val title: String,
    val caption: String
)