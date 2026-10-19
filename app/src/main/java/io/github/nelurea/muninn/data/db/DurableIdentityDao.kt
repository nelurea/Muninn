package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DurableIdentityDao {
    @Query("SELECT stableId FROM durable_identities WHERE entityType = :entityType AND rowId = :rowId")
    suspend fun getStableId(entityType: String, rowId: Long): String?

    @Query("SELECT rowId FROM durable_identities WHERE entityType = :entityType AND stableId = :stableId")
    suspend fun getLocalId(entityType: String, stableId: String): Long?

    @Query("SELECT * FROM durable_identities WHERE entityType = :entityType ORDER BY rowId ASC")
    suspend fun getAllForType(entityType: String): List<DurableIdentityEntity>
}
