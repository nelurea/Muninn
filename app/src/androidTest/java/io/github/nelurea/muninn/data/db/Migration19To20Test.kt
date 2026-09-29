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
class Migration19To20Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-19-20-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationMakesLegacyImagesAvailableToCapturedWorkGallery() {
        context.deleteDatabase(databaseName)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE images (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                imageUri TEXT NOT NULL,
                                createdAt INTEGER NOT NULL,
                                sessionId INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE captured_works (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sourceType TEXT NOT NULL,
                                sourceId TEXT NOT NULL,
                                canonicalUrl TEXT NOT NULL,
                                capturedAt TEXT NOT NULL,
                                publishedAt TEXT,
                                discoveryMode TEXT,
                                discoveryQuery TEXT,
                                authorId TEXT NOT NULL,
                                authorName TEXT NOT NULL,
                                authorHandle TEXT,
                                title TEXT,
                                caption TEXT NOT NULL,
                                contentRestriction TEXT NOT NULL,
                                sessionId INTEGER
                            )
                            """.trimIndent()
                        )
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
                                isHighlighted INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO images VALUES (1, 'content://legacy/one', 1723564800123, 4)"
                        )
                        db.execSQL(
                            "INSERT INTO images VALUES (2, 'content://legacy/two', 1723651200456, 5)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_19_20.migrate(db)
                    }
                }).build()
        )

        val db = requireNotNull(helper).writableDatabase
        MIGRATION_19_20.migrate(db)
        MIGRATION_19_20.migrate(db)

        db.query(
            """
            SELECT captured_works.sourceId, captured_media.localUri,
                   captured_media.mimeType, captured_works.sessionId
            FROM captured_works
            INNER JOIN captured_media
                ON captured_media.workId = captured_works.id
            WHERE captured_works.sourceType = 'legacy'
            ORDER BY captured_works.sourceId
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("image-1", cursor.getString(0))
            assertEquals("content://legacy/one", cursor.getString(1))
            assertEquals("image/*", cursor.getString(2))
            assertEquals(4L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals("image-2", cursor.getString(0))
            assertEquals("content://legacy/two", cursor.getString(1))
            assertEquals("image/*", cursor.getString(2))
            assertEquals(5L, cursor.getLong(3))
            assertTrue(!cursor.moveToNext())
        }
    }
}
