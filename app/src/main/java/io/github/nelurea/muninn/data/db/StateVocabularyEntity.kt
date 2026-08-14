package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "state_vocabulary",
    indices = [
        Index(
            value = ["label"],
            unique = true
        )
    ]
)
data class StateVocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val label: String
)
