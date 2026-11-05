package io.github.nelurea.muninn.data.context

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.installDurableIdentityTriggers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ContextEmptyRoomRestorerTest {
    private fun database(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
    ).addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = installDurableIdentityTriggers(db)
    }).build()

    @Test fun restoresAllRowsAndStableIdentities() {
        val source = database()
        val target = database()
        try {
            seed(source.openHelper.writableDatabase)
            val snapshot = RoomContextSnapshotExporter(source).export()
            val inspected = ContextEmptyRoomRestorer(target).restore(snapshot)
            assertEquals(5, inspected.stableIdentityCount)
            assertTrue(inspected.rowCounts.values.all { it > 0 })
            assertEquivalent(snapshot, RoomContextSnapshotExporter(target).export())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun rejectsNonEmptyTargetWithoutChangingIt() {
        val source = database()
        val target = database()
        try {
            seed(source.openHelper.writableDatabase)
            target.openHelper.writableDatabase.execSQL(
                "INSERT INTO sessions (id, createdAt, lastActivityAt) VALUES (90, 1, 1)"
            )
            val before = RoomContextSnapshotExporter(target).export()
            assertThrows(IOException::class.java) {
                ContextEmptyRoomRestorer(target).restore(RoomContextSnapshotExporter(source).export())
            }
            assertEquivalent(before, RoomContextSnapshotExporter(target).export())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun rejectsOperationalRowsEvenWhenContextTablesAreEmpty() {
        val source = database()
        val target = database()
        try {
            val sql = target.openHelper.writableDatabase
            sql.execSQL(
                "INSERT INTO pending_capture (sourceUrl, createdAt) VALUES ('https://example.invalid', 1)"
            )
            assertThrows(IOException::class.java) {
                ContextEmptyRoomRestorer(target).restore(RoomContextSnapshotExporter(source).export())
            }
            assertEquals(1L, count(target, "pending_capture"))
            assertEquals(0L, count(target, "sessions"))
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun rollsBackOnConstraintFailureAfterSomeRowsWereInserted() {
        val source = database()
        val target = database()
        try {
            seed(source.openHelper.writableDatabase)
            val bad = JSONObject(String(RoomContextSnapshotExporter(source).export(), Charsets.UTF_8))
            val work = bad.getJSONObject("tables").getJSONObject("captured_works")
            val columns = work.getJSONArray("columns")
            val required = (0 until columns.length()).first { columns.getString(it) == "sourceType" }
            work.getJSONArray("rows").getJSONArray(0).put(required, JSONObject.NULL)
            val invalid = bad.toString().toByteArray(Charsets.UTF_8)
            ContextRestorePreflight(target).inspect(invalid)
            assertThrows(Exception::class.java) {
                ContextEmptyRoomRestorer(target).restore(invalid)
            }
            assertEquals(0L, count(target, "sessions"))
            assertEquals(0L, count(target, "captured_works"))
            assertEquals(0L, count(target, "durable_identities"))
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun rejectsDanglingWorkSessionBeforeWriting() {
        val source = database()
        val target = database()
        try {
            seed(source.openHelper.writableDatabase)
            val bad = JSONObject(String(RoomContextSnapshotExporter(source).export(), Charsets.UTF_8))
            val work = bad.getJSONObject("tables").getJSONObject("captured_works")
            val columns = work.getJSONArray("columns")
            val session = (0 until columns.length()).first { columns.getString(it) == "sessionId" }
            work.getJSONArray("rows").getJSONArray(0).put(session, "i:987654321")
            assertThrows(IOException::class.java) {
                ContextEmptyRoomRestorer(target).restore(bad.toString().toByteArray(Charsets.UTF_8))
            }
            assertEquals(0L, count(target, "sessions"))
        } finally {
            source.close()
            target.close()
        }
    }

    private fun count(db: AppDatabase, table: String): Long =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun seed(sql: SupportSQLiteDatabase) {
        sql.execSQL("INSERT INTO sessions (id,createdAt,lastActivityAt) VALUES (7,9223372036854775000,100)")
        sql.execSQL("""INSERT INTO captured_works
            (id,sourceType,sourceId,canonicalUrl,capturedAt,authorId,authorName,title,caption,contentRestriction,sessionId)
            VALUES (10,'pixiv','123','https://example.invalid/123','2026-01-01','author','A','Work','Caption','UNKNOWN',7)
        """.trimIndent())
        sql.execSQL("""INSERT INTO captured_media
            (id,workId,mediaIndex,localUri,sourceUrl,mimeType,fileName,isHighlighted)
            VALUES (20,10,0,'content://example.invalid/image','https://example.invalid/media','image/jpeg','one.jpg',1)
        """.trimIndent())
        sql.execSQL("INSERT INTO images (id,imageUri,createdAt,sessionId) VALUES (30,'content://example.invalid/legacy',200,7)")
        sql.execSQL("INSERT INTO purpose_vocabulary (id,label,lastUsedAt) VALUES (9,'Reference',50)")
        sql.execSQL("INSERT INTO session_purposes (sessionId,purposeVocabularyId) VALUES (7,9)")
        sql.execSQL("INSERT INTO captured_work_purposes (workId,purposeVocabularyId) VALUES (10,9)")
        sql.execSQL("INSERT INTO captured_tags (workId,position,tag) VALUES (10,0,'sunset')")
        sql.execSQL("INSERT INTO attraction_vocabulary (id,dimension,label) VALUES (11,'visual','color')")
        sql.execSQL("INSERT INTO captured_work_attractions (workId,attractionVocabularyId) VALUES (10,11)")
        sql.execSQL("INSERT INTO captured_media_attractions (mediaId,attractionVocabularyId) VALUES (20,11)")
        sql.execSQL("INSERT INTO aesthetic_response_vocabulary (id,label) VALUES (12,'Inspired')")
        sql.execSQL("INSERT INTO captured_work_responses (workId,responseVocabularyId) VALUES (10,12)")
        sql.execSQL("INSERT INTO state_vocabulary (id,label) VALUES (13,'Reflective')")
        sql.execSQL("INSERT INTO session_states (sessionId,stateVocabularyId) VALUES (7,13)")
        sql.execSQL("""INSERT INTO media_focus
            (id,mediaId,attractionVocabularyId,note,regionLeft,regionTop,regionRight,regionBottom)
            VALUES (40,20,11,'detail',0.125,0.25,0.875,0.75)
        """.trimIndent())
        sql.execSQL("""INSERT INTO save_events
            (id,sourceType,sourceId,origin,legacyWorkId,sessionId,canonicalWorkId,saveKind)
            VALUES (50,'pixiv','123','CAPTURE',10,7,10,'NEW_CAPTURE')
        """.trimIndent())
        sql.execSQL("""INSERT INTO save_event_media
            (id,saveEventId,capturedMediaId,mediaIndex,localUri,isLegacyBackfill)
            VALUES (60,50,20,0,'content://example.invalid/image',0)
        """.trimIndent())
    }

    private fun assertEquivalent(a: ByteArray, b: ByteArray) {
        val left = JSONObject(String(a, Charsets.UTF_8)).getJSONObject("tables")
        val right = JSONObject(String(b, Charsets.UTF_8)).getJSONObject("tables")
        for (name in RoomContextSnapshotExporter.TABLE_NAMES) {
            val before = left.getJSONObject(name)
            val after = right.getJSONObject(name)
            assertEquals("Columns: $name", before.getJSONArray("columns").toString(), after.getJSONArray("columns").toString())
            fun rows(table: JSONObject): List<String> {
                val values = table.getJSONArray("rows")
                return (0 until values.length()).map { values.getJSONArray(it).toString() }.sorted()
            }
            assertEquals("Rows: $name", rows(before), rows(after))
        }
    }
}
