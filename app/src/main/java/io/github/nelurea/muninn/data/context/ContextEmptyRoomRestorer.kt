package io.github.nelurea.muninn.data.context

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.nelurea.muninn.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ContextEmptyRoomRestorer(private val database: AppDatabase) {
    fun restore(snapshot: ByteArray): ContextRestorePreflight.Result {
        val inspected = ContextRestorePreflight(database).inspect(snapshot)
        val tables = JSONObject(String(snapshot, Charsets.UTF_8)).getJSONObject("tables")
        val sql = database.openHelper.writableDatabase

        sql.beginTransaction()
        try {
            if (sql.version != 23) throw IOException("Unsupported Room version")
            requireEmptyDatabase(sql)
            checkWorkSessions(tables)

            for (table in insertionOrder(sql)) {
                insertRows(sql, table, tables.getJSONObject(table))
            }

            sql.execSQL("DELETE FROM `durable_identities`")
            insertRows(sql, "durable_identities", tables.getJSONObject("durable_identities"))

            sql.query("PRAGMA foreign_key_check").use {
                if (it.moveToFirst()) throw IOException("Restored Context has broken foreign keys")
            }

            val roundTrip = RoomContextSnapshotExporter(database).export()
            if (!sameContents(tables, JSONObject(String(roundTrip, Charsets.UTF_8)).getJSONObject("tables"))) {
                throw IOException("Restored Context differs from the source snapshot")
            }

            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        return inspected
    }

    private fun requireEmptyDatabase(sql: SupportSQLiteDatabase) {
        sql.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                if (table.startsWith("sqlite_") || table in setOf(
                        "room_master_table", "room_table_modification_log", "android_metadata"
                    )) continue
                if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(table)) {
                    throw IOException("Unexpected database table")
                }
                sql.query("SELECT 1 FROM `$table` LIMIT 1").use { rows ->
                    if (rows.moveToFirst()) throw IOException("Restore requires an empty Room database: $table")
                }
            }
        }
    }

    private fun insertionOrder(sql: SupportSQLiteDatabase): List<String> {
        val tables = RoomContextSnapshotExporter.TABLE_NAMES.filterNot { it == "durable_identities" }
        val parents = tables.associateWith { table ->
            val referenced = mutableSetOf<String>()
            sql.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
                val targetColumn = cursor.getColumnIndexOrThrow("table")
                while (cursor.moveToNext()) referenced += cursor.getString(targetColumn)
            }
            if (!tables.containsAll(referenced)) throw IOException("Unexpected Context foreign key in $table")
            referenced
        }
        val ordered = mutableListOf<String>()
        val remaining = tables.toMutableSet()
        while (remaining.isNotEmpty()) {
            val ready = tables.firstOrNull { it in remaining && ordered.containsAll(parents.getValue(it)) }
                ?: throw IOException("Cyclic Context foreign keys")
            ordered += ready
            remaining.remove(ready)
        }
        return ordered
    }

    private fun checkWorkSessions(tables: JSONObject) {
        val sessionTable = tables.getJSONObject("sessions")
        val sessionIndex = columnIndex(sessionTable, "id")
        val sessionIds = (0 until sessionTable.getJSONArray("rows").length()).map { row ->
            decode(sessionTable.getJSONArray("rows").getJSONArray(row), sessionIndex) as? Long
                ?: throw IOException("Invalid Session ID")
        }.toSet()

        val works = tables.getJSONObject("captured_works")
        val workSessionIndex = columnIndex(works, "sessionId")
        val rows = works.getJSONArray("rows")
        for (i in 0 until rows.length()) {
            val sessionId = decode(rows.getJSONArray(i), workSessionIndex) ?: continue
            if (sessionId !is Long || sessionId !in sessionIds) {
                throw IOException("Work references a missing Session")
            }
        }
    }

    private fun insertRows(sql: SupportSQLiteDatabase, table: String, archive: JSONObject) {
        val columns = archive.getJSONArray("columns")
        val rows = archive.getJSONArray("rows")
        for (index in 0 until rows.length()) {
            val values = ContentValues(columns.length())
            val row = rows.getJSONArray(index)
            for (columnIndex in 0 until columns.length()) {
                val column = columns.getString(columnIndex)
                when (val value = decode(row, columnIndex)) {
                    null -> values.putNull(column)
                    is Long -> values.put(column, value)
                    is Double -> values.put(column, value)
                    is String -> values.put(column, value)
                    is ByteArray -> values.put(column, value)
                    else -> throw IOException("Unsupported Context value")
                }
            }
            if (sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) == -1L) {
                throw IOException("Unable to restore $table row")
            }
        }
    }

    private fun columnIndex(table: JSONObject, name: String): Int {
        val columns = table.getJSONArray("columns")
        for (index in 0 until columns.length()) {
            if (columns.getString(index) == name) return index
        }
        throw IOException("Missing Context column: $name")
    }

    private fun decode(row: JSONArray, index: Int): Any? {
        if (row.isNull(index)) return null
        val encoded = row.getString(index)
        if (encoded.length < 2 || encoded[1] != ':') throw IOException("Invalid Context encoding")
        val data = encoded.substring(2)
        return when (encoded[0]) {
            'i' -> data.toLongOrNull() ?: throw IOException("Invalid Context integer")
            'f' -> data.toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: throw IOException("Invalid Context float")
            's' -> data
            'b' -> try { Base64.decode(data, Base64.DEFAULT) }
                catch (exception: IllegalArgumentException) { throw IOException("Invalid Context blob", exception) }
            else -> throw IOException("Unknown Context encoding")
        }
    }

    private fun sameContents(before: JSONObject, after: JSONObject): Boolean =
        RoomContextSnapshotExporter.TABLE_NAMES.all { table ->
            val original = before.getJSONObject(table)
            val restored = after.getJSONObject(table)
            if (original.getJSONArray("columns").toString() != restored.getJSONArray("columns").toString()) {
                false
            } else {
                fun sortedRows(content: JSONObject): List<String> {
                    val rows = content.getJSONArray("rows")
                    return (0 until rows.length()).map { rows.getJSONArray(it).toString() }.sorted()
                }
                sortedRows(original) == sortedRows(restored)
            }
        }
}
