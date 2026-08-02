package io.github.nelurea.muninn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val createdAt: Long = System.currentTimeMillis(),

    val lastActivityAt: Long = System.currentTimeMillis()
)