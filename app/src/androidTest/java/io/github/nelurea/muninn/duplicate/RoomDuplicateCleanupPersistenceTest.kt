package io.github.nelurea.muninn.duplicate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.DuplicateCleanupState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDuplicateCleanupPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var persistence: RoomDuplicateCleanupPersistence

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        persistence = RoomDuplicateCleanupPersistence(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pendingEntryUsesTransactionCurrentUriAndCompletes() = runBlocking {
        insertCleanup("old")
        val stale = persistence.pending().single()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE duplicate_cleanup_journal SET targetUri='current' WHERE id=1"
        )
        val deleted = mutableListOf<String>()

        persistence.process(stale, 2) { uri ->
            deleted += uri
            DuplicateCleanupDeleteResult.Deleted
        }

        assertEquals(listOf("current"), deleted)
        assertEquals(DuplicateCleanupState.COMPLETED, cleanupState())
    }

    @Test
    fun staleCompletedEntryIsSkipped() = runBlocking {
        insertCleanup("target")
        val stale = persistence.pending().single()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE duplicate_cleanup_journal SET state='COMPLETED' WHERE id=1"
        )
        var deletes = 0

        persistence.process(stale, 2) { deletes++; DuplicateCleanupDeleteResult.Deleted }

        assertEquals(0, deletes)
        assertEquals(DuplicateCleanupState.COMPLETED, cleanupState())
    }

    @Test
    fun referencedCurrentUriIsRejectedWithoutDeletion() = runBlocking {
        insertCleanup("old")
        val stale = persistence.pending().single()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE duplicate_cleanup_journal SET targetUri='current' WHERE id=1")
        sql.execSQL("INSERT INTO captured_works VALUES (10,'test','work','', '1',NULL,NULL,NULL,'a','a',NULL,NULL,'',NULL)")
        sql.execSQL("INSERT INTO captured_media VALUES (10,10,0,'current','','image/jpeg','current.jpg',0)")
        var deletes = 0

        persistence.process(stale, 2) { deletes++; DuplicateCleanupDeleteResult.Deleted }

        assertEquals(0, deletes)
        assertEquals(DuplicateCleanupState.PENDING, cleanupState())
        assertEquals(DuplicateCleanupError.URI_STILL_REFERENCED, cleanupError())
    }

    @Test
    fun completionCasFailureRollsBackAndRetryRecoversThroughMissing() = runBlocking {
        insertCleanup("target")
        val entry = persistence.pending().single()
        var targetExists = true

        try {
            persistence.process(entry, 2) {
                targetExists = false
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE duplicate_cleanup_journal SET state='COMPLETED' WHERE id=1"
                )
                DuplicateCleanupDeleteResult.Deleted
            }
            throw AssertionError("completion CAS failure must throw")
        } catch (_: IllegalStateException) {
            // The database update is rolled back; the external deletion is not.
        }

        assertEquals(false, targetExists)
        assertEquals(DuplicateCleanupState.PENDING, cleanupState())
        persistence.process(entry, 3) {
            if (targetExists) DuplicateCleanupDeleteResult.Deleted else DuplicateCleanupDeleteResult.Missing
        }
        assertEquals(DuplicateCleanupState.COMPLETED, cleanupState())
    }

    private fun insertCleanup(uri: String) {
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO duplicate_normalization_journal VALUES (1,'test','work','COMPLETED','VERIFIED',NULL,NULL,NULL,NULL,NULL,1,1)"
        )
        sql.execSQL(
            "INSERT INTO duplicate_cleanup_journal VALUES (1,1,NULL,?, 'PENDING',NULL,1)", arrayOf(uri)
        )
    }

    private fun cleanupState(): String = text("SELECT state FROM duplicate_cleanup_journal WHERE id=1")

    private fun cleanupError(): String = text("SELECT lastError FROM duplicate_cleanup_journal WHERE id=1")

    private fun text(query: String): String = database.openHelper.writableDatabase.query(query).use { cursor ->
        cursor.moveToFirst()
        cursor.getString(0)
    }
}
