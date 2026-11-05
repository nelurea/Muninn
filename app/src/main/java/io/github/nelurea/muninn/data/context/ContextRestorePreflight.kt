package io.github.nelurea.muninn.data.context

import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.nelurea.muninn.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.util.Base64

class ContextRestorePreflight(private val database: AppDatabase) {
    data class Result(val rowCounts: Map<String, Int>, val stableIdentityCount: Int)

    fun inspect(snapshot: ByteArray): Result {
        val counts = RoomContextSnapshotExporter.validate(snapshot)
        val sql = database.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            if (sql.version != 23) throw IOException("Unsupported Room schema version")
            val tables = JSONObject(String(snapshot, Charsets.UTF_8)).getJSONObject("tables")
            val data = linkedMapOf<String, Table>()
            for (name in RoomContextSnapshotExporter.TABLE_NAMES) {
                val obj = tables.getJSONObject(name)
                val columns = stringList(obj.getJSONArray("columns"))
                val actual = mutableListOf<String>()
                sql.query("PRAGMA table_info(`$name`)").use { cursor ->
                    while (cursor.moveToNext()) actual += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                if (columns != actual) throw IOException("Schema columns differ: $name")
                val rows = obj.getJSONArray("rows")
                val converted = ArrayList<List<Any?>>(rows.length())
                for (i in 0 until rows.length()) {
                    val src = rows.getJSONArray(i)
                    converted += (0 until src.length()).map { decode(src, it, name) }
                }
                data[name] = Table(columns, converted)
            }
            checkDurableIdentities(data)
            checkForeignKeys(sql, data)
            return Result(counts, data.getValue("durable_identities").rows.size)
        } finally {
            sql.endTransaction()
        }
    }

    private data class Table(val columns: List<String>, val rows: List<List<Any?>>) {
        fun index(column: String): Int = columns.indexOf(column).takeIf { it >= 0 }
            ?: throw IOException("Missing column $column")
        fun values(column: String): List<Any?> = rows.map { it[index(column)] }
    }

    private fun checkDurableIdentities(data: Map<String, Table>) {
        val identity = data.getValue("durable_identities")
        val typeIndex = identity.index("entityType")
        val rowIndex = identity.index("rowId")
        val stableIndex = identity.index("stableId")
        val mapping = linkedMapOf<Pair<String, Long>, String>()
        val unique = hashSetOf<String>()
        val types = mapOf(
            "session" to "sessions", "work" to "captured_works",
            "media" to "captured_media", "legacy_image" to "images", "focus" to "media_focus"
        )
        for (row in identity.rows) {
            val type = row[typeIndex] as? String ?: throw IOException("Invalid identity type")
            val rowId = row[rowIndex] as? Long ?: throw IOException("Invalid identity row ID")
            val stable = row[stableIndex] as? String ?: throw IOException("Invalid stable ID")
            if (type !in types || !Regex("[0-9a-f]{32}").matches(stable) ||
                mapping.put(type to rowId, stable) != null || !unique.add(stable)) {
                throw IOException("Invalid or duplicate durable identity")
            }
        }
        for ((type, tableName) in types) {
            val ids = hashSetOf<Long>()
            for (value in data.getValue(tableName).values("id")) {
                val id = value as? Long ?: throw IOException("Invalid $tableName ID")
                if (!ids.add(id) || !mapping.containsKey(type to id)) {
                    throw IOException("Duplicate row or missing identity in $tableName")
                }
            }
            if (mapping.keys.count { it.first == type } != ids.size) {
                throw IOException("Orphan durable identities for $tableName")
            }
        }
    }

    private data class ForeignColumn(val group: Int, val seq: Int, val from: String, val target: String, val to: String)

    private fun checkForeignKeys(sql: SupportSQLiteDatabase, data: Map<String, Table>) {
        for ((name, source) in data) {
            val foreign = mutableListOf<ForeignColumn>()
            sql.query("PRAGMA foreign_key_list(`$name`)").use { cursor ->
                while (cursor.moveToNext()) {
                    fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
                    foreign += ForeignColumn(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("seq")),
                        text("from"), text("table"), text("to")
                    )
                }
            }
            for (group in foreign.groupBy { it.group }.values) {
                val fields = group.sortedBy { it.seq }
                val target = data[fields.first().target]
                    ?: throw IOException("Foreign key references non-exported table from $name")
                val sourceIndexes = fields.map { source.index(it.from) }
                val targetIndexes = fields.map { target.index(it.to) }
                val targetKeys = target.rows.map { row -> targetIndexes.map { row[it] } }.toHashSet()
                for (row in source.rows) {
                    val key = sourceIndexes.map { row[it] }
                    if (key.any { it == null }) continue
                    if (key !in targetKeys) throw IOException("Broken foreign key in $name")
                }
            }
        }
    }

    private fun stringList(array: JSONArray): List<String> =
        (0 until array.length()).map { array.getString(it) }

    private fun decode(row: JSONArray, index: Int, table: String): Any? {
        if (row.isNull(index)) return null
        val encoded = row.opt(index) as? String ?: throw IOException("Invalid cell in $table")
        if (encoded.length < 2 || encoded[1] != ':') throw IOException("Invalid encoding in $table")
        val body = encoded.substring(2)
        return try {
            when (encoded[0]) {
                'i' -> body.toLongOrNull() ?: throw IOException("Invalid integer in $table")
                'f' -> body.toDouble().also {
                    if (!it.isFinite()) throw IOException("Non-finite value in $table")
                }
                's' -> body
                'b' -> Base64.decode(body, Base64.DEFAULT)
                else -> throw IOException("Unsupported cell in $table")
            }
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid encoded cell in $table", error)
        }
    }
}
