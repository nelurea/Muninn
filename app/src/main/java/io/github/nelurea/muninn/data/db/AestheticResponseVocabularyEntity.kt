package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "aesthetic_response_vocabulary",
    indices = [
        Index(
            value = ["label"],
            unique = true
        )
    ]
)
data class AestheticResponseVocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val label: String
)