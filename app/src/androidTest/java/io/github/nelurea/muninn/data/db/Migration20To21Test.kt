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
class Migration20To21Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-20-21-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After fun tearDown() {
        helper?.close()
        context.deleteDatabase(dbName)
    }

    @Test fun upgradePreservesWorksAndIntroducesSessionPurposeAndRecency() {
        context.deleteDatabase(dbName)
        val oldHelper = helperWithCallback(object : SupportSQLiteOpenHelper.Callback(20) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY NOT NULL)")
                db.execSQL("CREATE TABLE purpose_vocabulary (id INTEGER PRIMARY KEY NOT NULL, label TEXT NOT NULL)")
                db.execSQL("CREATE TABLE captured_works (id INTEGER PRIMARY KEY NOT NULL, sessionId INTEGER)")
                db.execSQL("INSERT INTO sessions VALUES (5)")
                db.execSQL("INSERT INTO purpose_vocabulary VALUES (9, 'Reference')")
                db.execSQL("INSERT INTO captured_works VALUES (10, 5)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected old schema upgrade")
            }
        })
        oldHelper.writableDatabase
        oldHelper.close()

        helper = helperWithCallback(object : SupportSQLiteOpenHelper.Callback(21) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("Database was unexpectedly recreated")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(20, oldVersion)
                assertEquals(21, newVersion)
                MIGRATION_20_21.migrate(db)
            }
        })
        val db = requireNotNull(helper).writableDatabase
        db.query("SELECT sessionId, isPurposeDetour FROM captured_works WHERE id = 10").use {
            assertTrue(it.moveToFirst())
            assertEquals(5L, it.getLong(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT lastUsedAt FROM purpose_vocabulary WHERE id = 9").use {
            assertTrue(it.moveToFirst())
            assertEquals(0L, it.getLong(0))
        }
        db.execSQL("INSERT INTO session_purposes (sessionId, purposeVocabularyId) VALUES (5, 9)")
        db.query("SELECT purposeVocabularyId FROM session_purposes WHERE sessionId = 5").use {
            assertTrue(it.moveToFirst())
            assertEquals(9L, it.getLong(0))
        }
        db.execSQL("UPDATE captured_works SET isPurposeDetour = 1 WHERE id = 10")
        db.query("SELECT isPurposeDetour FROM captured_works WHERE id = 10").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    private fun helperWithCallback(callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
}
