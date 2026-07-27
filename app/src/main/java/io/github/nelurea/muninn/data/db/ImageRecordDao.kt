package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageRecordDao {

    @Insert
    suspend fun insert(record: ImageRecord)

    @Query("SELECT * FROM images ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ImageRecord>>
}