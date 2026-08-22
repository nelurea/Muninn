package io.github.nelurea.muninn.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration17To18Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-17-18-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationAddsOnlyV18SchemaAndPreservesLegacyRows() {
        context.deleteDatabase(databaseName)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE captured_works (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sourceType TEXT NOT NULL,
                                sourceId TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO captured_works(id, sourceType, sourceId) " +
                                "VALUES (7, 'PIXIV', '123')"
                        )
                        db.execSQL(
                            """
                            CREATE TABLE captured_media (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                workId INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE sessions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                createdAt INTEGER NOT NULL,
                                lastActivityAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_17_18.migrate(db)
                    }
                }).build()
        )

        val db = requireNotNull(helper).writableDatabase

        db.query("SELECT sourceType, sourceId FROM captured_works WHERE id = 7").use {
            assertTrue(it.moveToFirst())
            assertEquals("PIXIV", it.getString(0))
            assertEquals("123", it.getString(1))
        }
        assertEquals(
            setOf(
                "save_events",
                "save_event_media",
                "duplicate_normalization_journal",
                "duplicate_cleanup_journal"
            ),
            tableNames(db).intersect(
                setOf(
                    "save_events",
                    "save_event_media",
                    "duplicate_normalization_journal",
                    "duplicate_cleanup_journal"
                )
            )
        )
        assertTrue(nullableColumns(db, "save_events").containsAll(
            setOf(
                "sourceType", "sourceId", "canonicalUrl", "savedAt", "legacyWorkId", "sessionId",
                "canonicalWorkId", "discoveryMode", "discoveryQuery"
            )
        ))
        assertTrue(columns(db, "save_events").contains("saveKind"))
        assertFalse(nullableColumns(db, "save_events").contains("saveKind"))
        assertTrue(nullableColumns(db, "save_event_media").containsAll(
            setOf(
                "capturedMediaId", "mediaIndex", "localUri", "sourceUrl", "mimeType", "fileName",
                "wasRequested", "wasHighlighted", "wasNewlyStored"
            )
        ))
        assertTrue(columns(db, "save_event_media").contains("isLegacyBackfill"))
        assertFalse(nullableColumns(db, "save_event_media").contains("isLegacyBackfill"))
        assertTrue(columns(db, "duplicate_normalization_journal").containsAll(
            setOf("verificationState", "verificationDetails", "canonicalWorkId", "planVersion", "planJson")
        ))
        assertTrue(columns(db, "duplicate_cleanup_journal").containsAll(
            setOf("normalizationId", "capturedMediaId", "targetUri", "state", "lastError")
        ))
        assertTrue(indexNames(db, "save_events").contains("index_save_events_sourceType_sourceId"))
        assertTrue(indexNames(db, "save_events").contains("index_save_events_sessionId"))
        assertTrue(indexNames(db, "save_events").contains("index_save_events_canonicalWorkId"))
        assertTrue(indexNames(db, "save_event_media").contains(
            "index_save_event_media_capturedMediaId"
        ))
        assertEquals(
            setOf(
                Triple("sessions", "sessionId", "SET NULL"),
                Triple("captured_works", "canonicalWorkId", "SET NULL")
            ),
            foreignKeys(db, "save_events").toSet()
        )
        assertEquals(
            setOf(
                Triple("save_events", "saveEventId", "CASCADE"),
                Triple("captured_media", "capturedMediaId", "SET NULL")
            ),
            foreignKeys(db, "save_event_media").toSet()
        )
        assertTrue(indexNames(db, "duplicate_cleanup_journal").contains(
            "index_duplicate_cleanup_journal_normalizationId_targetUri"
        ))
        assertFalse(tableNames(db).contains("captured_works_new"))
    }

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

    private fun nullableColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(3) == 0 && cursor.getInt(5) == 0) add(cursor.getString(1))
                }
            }
        }

    private fun indexNames(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

    private fun foreignKeys(
        db: SupportSQLiteDatabase,
        table: String
    ): List<Triple<String, String, String>> =
        db.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Triple(
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getString(6)
                        )
                    )
                }
            }
        }
}
