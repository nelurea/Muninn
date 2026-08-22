package io.github.nelurea.muninn.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration16To17Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-16-17-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationCreatesJournalAndPreservesCapturedMedia() {
        context.deleteDatabase(databaseName)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE captured_works (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                        db.execSQL(
                            """
                            CREATE TABLE captured_media (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                workId INTEGER NOT NULL,
                                mediaIndex INTEGER NOT NULL,
                                localUri TEXT NOT NULL,
                                sourceUrl TEXT NOT NULL,
                                mimeType TEXT NOT NULL,
                                fileName TEXT NOT NULL,
                                isHighlighted INTEGER NOT NULL,
                                FOREIGN KEY(workId) REFERENCES captured_works(id) ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO captured_works(id) VALUES (7)")
                        db.execSQL("INSERT INTO captured_media VALUES (9, 7, 0, 'file:///old.jpg', '', 'image/jpeg', 'old.jpg', 0)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        MIGRATION_16_17.migrate(db)
                    }
                }).build()
        )

        val db = requireNotNull(helper).writableDatabase
        MIGRATION_16_17.migrate(db)
        db.query("SELECT id, localUri FROM captured_media WHERE id = 9").use {
            assertTrue(it.moveToFirst())
            assertEquals(9L, it.getLong(0))
            assertEquals("file:///old.jpg", it.getString(1))
        }
        db.query("PRAGMA table_info(media_move_journal)").use {
            val columns = mutableSetOf<String>()
            while (it.moveToNext()) columns += it.getString(1)
            assertTrue(columns.containsAll(setOf("mediaId", "sourceUri", "destinationUri", "state", "updatedAt")))
        }
    }
}
