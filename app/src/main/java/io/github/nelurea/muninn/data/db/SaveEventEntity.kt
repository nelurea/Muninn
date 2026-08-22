package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "save_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CapturedWorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["canonicalWorkId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["sourceType", "sourceId"]),
        Index(value = ["legacyWorkId"]),
        Index(value = ["sessionId"]),
        Index(value = ["canonicalWorkId"])
    ]
)
data class SaveEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceType: String?,
    val sourceId: String?,
    val canonicalUrl: String?,
    val savedAt: String?,
    val origin: String = SaveEventOrigin.UNKNOWN,
    val legacyWorkId: Long? = null,
    val sessionId: Long? = null,
    val canonicalWorkId: Long? = null,
    val discoveryMode: String? = null,
    val discoveryQuery: String? = null,
    val saveKind: String = SaveKind.LEGACY
)

object SaveKind {
    const val NEW_CAPTURE = "NEW_CAPTURE"
    const val RESAVE = "RESAVE"
    const val MEDIA_APPEND = "MEDIA_APPEND"
    const val LEGACY = "LEGACY"
}

object SaveEventOrigin {
    const val UNKNOWN = "UNKNOWN"
    const val CAPTURE = "CAPTURE"
    const val LEGACY = "LEGACY"
}
