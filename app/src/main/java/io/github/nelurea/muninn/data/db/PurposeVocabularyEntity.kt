package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purpose_vocabulary",
    indices = [
        Index(
            value = ["label"],
            unique = true
        )
    ]
)
data class PurposeVocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val label: String
)
