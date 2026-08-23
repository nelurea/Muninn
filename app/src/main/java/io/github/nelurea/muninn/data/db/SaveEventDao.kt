package io.github.nelurea.muninn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class SaveEventDao {
    @Insert
    protected abstract suspend fun insertEvent(event: SaveEventEntity): Long

    @Insert
    protected abstract suspend fun insertMedia(media: List<SaveEventMediaEntity>)

    @Transaction
    open suspend fun insert(event: SaveEventEntity, media: List<SaveEventMediaEntity>): Long {
        val eventId = insertEvent(event)
        insertMedia(media.map { it.copy(saveEventId = eventId) })
        return eventId
    }

    @Query("SELECT * FROM save_events WHERE id = :id")
    abstract suspend fun getEvent(id: Long): SaveEventEntity?

    @Query("SELECT * FROM save_event_media WHERE saveEventId = :saveEventId ORDER BY id")
    abstract suspend fun getMedia(saveEventId: Long): List<SaveEventMediaEntity>

    @Query("SELECT * FROM save_events WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY id")
    abstract suspend fun getBySourceIdentity(sourceType: String, sourceId: String): List<SaveEventEntity>
}
