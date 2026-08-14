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
    val images: List<ImageRecord>,

    @Relation(
        entity = CapturedWorkEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val capturedWorks: List<CapturedWorkWithMedia>,

    @Relation(
        entity = StateVocabularyEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room.Junction(
            value = SessionStateEntity::class,
            parentColumn = "sessionId",
            entityColumn = "stateVocabularyId"
        )
    )
    val states: List<StateVocabularyEntity>
)