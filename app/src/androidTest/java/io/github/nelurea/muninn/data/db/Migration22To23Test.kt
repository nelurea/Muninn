package io.github.nelurea.muninn.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a disposable test database; never touches muninn.db or user media. */
@RunWith(AndroidJUnit4::class)
class Migration22To23Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-22-to-23-durable-identities-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After fun tearDown() {
        helper?.close()
        // This is ONLY the private test fixture, never Muninn's user database.
        context.deleteDatabase(name)
    }

    @Test fun migrationKeepsExistingRowsAndRelationshipsAndTracksNewRows() {
        context.deleteDatabase(name)
        val oldHelper = openWithCallback(object : SupportSQLiteOpenHelper.Callback(22) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE captured_works (id INTEGER PRIMARY KEY NOT NULL, sessionId INTEGER)")
                db.execSQL("CREATE TABLE captured_media (id INTEGER PRIMARY KEY NOT NULL, workId INTEGER NOT NULL, localUri TEXT NOT NULL)")
                db.execSQL("CREATE TABLE images (id INTEGER PRIMARY KEY NOT NULL, sessionId INTEGER NOT NULL, imageUri TEXT NOT NULL)")
                db.execSQL("CREATE TABLE media_focus (id INTEGER PRIMARY KEY NOT NULL, mediaId INTEGER NOT NULL, note TEXT)")
                db.execSQL("CREATE TABLE purpose_vocabulary (id INTEGER PRIMARY KEY NOT NULL, label TEXT NOT NULL)")
                db.execSQL("CREATE TABLE session_purposes (sessionId INTEGER PRIMARY KEY NOT NULL, purposeVocabularyId INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE captured_work_purposes (workId INTEGER NOT NULL, purposeVocabularyId INTEGER NOT NULL)")
                db.execSQL("INSERT INTO sessions VALUES (7, 1234)")
                db.execSQL("INSERT INTO captured_works VALUES (10, 7)")
                db.execSQL("INSERT INTO captured_works VALUES (11, 7)")
                db.execSQL("INSERT INTO captured_media VALUES (20, 10, 'content://saved/one')")
                db.execSQL("INSERT INTO images VALUES (30, 7, 'content://saved/legacy')")
                db.execSQL("INSERT INTO media_focus VALUES (40, 20, 'important')")
                db.execSQL("INSERT INTO purpose_vocabulary VALUES (1, 'Reference')")
                db.execSQL("INSERT INTO session_purposes VALUES (7, 1)")
                db.execSQL("INSERT INTO captured_work_purposes VALUES (10, 1)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected old database upgrade")
            }
        })
        oldHelper.writableDatabase
        oldHelper.close()

        helper = openWithCallback(object : SupportSQLiteOpenHelper.Callback(23) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("Old database was unexpectedly recreated")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(22, oldVersion)
                assertEquals(23, newVersion)
                MIGRATION_22_23.migrate(db)
            }
        })
        val db = requireNotNull(helper).writableDatabase
        assertEquals("1234", single(db, "SELECT createdAt FROM sessions WHERE id = 7"))
        assertEquals("7", single(db, "SELECT sessionId FROM captured_works WHERE id = 10"))
        assertEquals("10", single(db, "SELECT workId FROM captured_media WHERE id = 20"))
        assertEquals("content://saved/one", single(db, "SELECT localUri FROM captured_media WHERE id = 20"))
        assertEquals("7", single(db, "SELECT sessionId FROM images WHERE id = 30"))
        assertEquals("20", single(db, "SELECT mediaId FROM media_focus WHERE id = 40"))
        assertEquals("1", single(db, "SELECT purposeVocabularyId FROM session_purposes WHERE sessionId = 7"))
        assertEquals("1", single(db, "SELECT purposeVocabularyId FROM captured_work_purposes WHERE workId = 10"))
        val ids = mapOf(
            "session" to 7, "work" to 10, "media" to 20,
            "legacy_image" to 30, "focus" to 40
        )
        val stable = ids.map { (type, id) -> identity(db, type, id) }
        assertEquals(5, stable.toSet().size)
        assertTrue(stable.all { it.length == 32 && it.matches(Regex("[0-9a-f]+")) })
        assertNotEquals(identity(db, "work", 10), identity(db, "work", 11))
        db.execSQL("UPDATE captured_media SET localUri = 'content://saved/moved' WHERE id = 20")
        assertEquals(stable[2], identity(db, "media", 20))
        db.execSQL("INSERT INTO captured_media VALUES (21, 10, 'content://saved/two')")
        assertTrue(identity(db, "media", 21).isNotBlank())
        db.execSQL("DELETE FROM media_focus WHERE id = 40")
        assertFalse(hasIdentity(db, "focus", 40))
    }


    @Test fun freshRoomSchemaCreatesIdentifiersForNewEntities() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    installDurableIdentityTriggers(db)
                }
            })
            .build()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO sessions (id, createdAt, lastActivityAt) VALUES (99, 10, 10)")
            assertEquals(32, identity(sql, "session", 99).length)
            sql.execSQL("DELETE FROM sessions WHERE id = 99")
            assertFalse(hasIdentity(sql, "session", 99))
        } finally {
            database.close()
        }
    }

    private fun identity(db: SupportSQLiteDatabase, type: String, id: Int): String =
        single(db, "SELECT stableId FROM durable_identities WHERE entityType = '$type' AND rowId = $id")

    private fun hasIdentity(db: SupportSQLiteDatabase, type: String, id: Int): Boolean =
        db.query("SELECT 1 FROM durable_identities WHERE entityType = '$type' AND rowId = $id")
            .use { it.moveToFirst() }

    private fun single(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor ->
            assertTrue("Missing row: $sql", cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun openWithCallback(callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build()
        )
}
