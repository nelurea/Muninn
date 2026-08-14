package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captured_works",
    indices = [
        Index(
            value = ["sourceType", "sourceId"]
        ),
        Index("sessionId")
    ]
)
data class CapturedWorkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sourceType: String,
    val sourceId: String,
    val canonicalUrl: String,
    val capturedAt: String,

    val publishedAt: String?,
    val discoveryMode: String?,
    val discoveryQuery: String?,

    val authorId: String,
    val authorName: String,
    val authorHandle: String? = null,
    val title: String?,
    val caption: String,

    val sessionId: Long?
)