package io.github.nelurea.muninn.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithImages(

    @Embedded
    val session: SessionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val images: List<ImageRecord>
)