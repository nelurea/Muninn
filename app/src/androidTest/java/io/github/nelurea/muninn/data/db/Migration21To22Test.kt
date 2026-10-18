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
class Migration21To22Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "metadata-recovery-migration.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After fun tearDown() {
        helper?.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationPreservesSavedWorksAndAddsSafeDefaults() {
        context.deleteDatabase(name)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE captured_works (id INTEGER PRIMARY KEY, sourceType TEXT NOT NULL, sourceId TEXT NOT NULL)")
                        db.execSQL("INSERT INTO captured_works VALUES (1, 'pixiv', '123')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        val database = requireNotNull(helper).writableDatabase
        MIGRATION_21_22.migrate(database)
        database.query(
            "SELECT sourceId, sourceAvailability, metadataRecoveryState, metadataRetryAfterMs FROM captured_works WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("123", cursor.getString(0))
            assertEquals("UNKNOWN", cursor.getString(1))
            assertEquals("UNKNOWN", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
    }
}
