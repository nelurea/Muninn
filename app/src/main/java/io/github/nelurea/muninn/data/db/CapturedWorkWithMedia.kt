package io.github.nelurea.muninn.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class CapturedWorkWithMedia(
    @Embedded
    val work: CapturedWorkEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "workId"
    )
    val media: List<CapturedMediaEntity>
)
