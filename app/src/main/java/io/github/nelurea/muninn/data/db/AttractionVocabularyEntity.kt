package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attraction_vocabulary",
    indices = [
        Index(
            value = [
                "dimension",
                "label"
            ],
            unique = true
        )
    ]
)
data class AttractionVocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val dimension: String,
    val label: String
)