package io.github.nelurea.muninn.data.context

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.installDurableIdentityTriggers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ContextRestorePreflightTest {
    private fun db(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
    ).addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = installDurableIdentityTriggers(db)
    }).build()

    @Test fun acceptsValidSnapshotWithoutChangingDatabase() {
        val database = db()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO sessions (id, createdAt, lastActivityAt) VALUES (17, 1, 1)")
            val bytes = RoomContextSnapshotExporter(database).export()
            val result = ContextRestorePreflight(database).inspect(bytes)
            assertEquals(1, result.rowCounts.getValue("sessions"))
            assertEquals(1, result.stableIdentityCount)
            sql.query("SELECT COUNT(*) FROM sessions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
            }
        } finally { database.close() }
    }

    @Test fun rejectsMissingIdentity() {
        val database = db()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO sessions (id, createdAt, lastActivityAt) VALUES (17, 1, 1)"
            )
            val json = JSONObject(String(RoomContextSnapshotExporter(database).export(), Charsets.UTF_8))
            json.getJSONObject("tables").getJSONObject("durable_identities").put("rows", JSONArray())
            assertThrows(IOException::class.java) {
                ContextRestorePreflight(database).inspect(json.toString().toByteArray())
            }
        } finally { database.close() }
    }

    @Test fun rejectsDanglingForeignKey() {
        val database = db()
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO sessions (id, createdAt, lastActivityAt) VALUES (17, 1, 1)")
            val json = JSONObject(String(RoomContextSnapshotExporter(database).export(), Charsets.UTF_8))
            val node = json.getJSONObject("tables").getJSONObject("images")
            val row = JSONArray()
            val columns = node.getJSONArray("columns")
            for (i in 0 until columns.length()) {
                val value = when (columns.getString(i)) {
                    "id" -> "i:40"
                    "sessionId" -> "i:99999"
                    "createdAt" -> "i:1"
                    "imageUri" -> "s:content://example/40"
                    else -> error("Unknown image column")
                }
                row.put(value)
            }
            node.getJSONArray("rows").put(row)
            val ids = json.getJSONObject("tables").getJSONObject("durable_identities")
            val idColumns = ids.getJSONArray("columns")
            val idRow = JSONArray()
            for (i in 0 until idColumns.length()) {
                idRow.put(when (idColumns.getString(i)) {
                    "entityType" -> "s:legacy_image"
                    "rowId" -> "i:40"
                    "stableId" -> "s:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    else -> error("Unknown identity column")
                })
            }
            ids.getJSONArray("rows").put(idRow)
            assertThrows(IOException::class.java) {
                ContextRestorePreflight(database).inspect(json.toString().toByteArray())
            }
        } finally { database.close() }
    }

    @Test fun rejectsSchemaColumnMismatch() {
        val database = db()
        try {
            val json = JSONObject(String(RoomContextSnapshotExporter(database).export(), Charsets.UTF_8))
            json.getJSONObject("tables").getJSONObject("sessions")
                .getJSONArray("columns").put("inventedColumn")
            assertThrows(IOException::class.java) {
                ContextRestorePreflight(database).inspect(json.toString().toByteArray())
            }
        } finally { database.close() }
    }
}
