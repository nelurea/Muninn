package io.github.nelurea.muninn.data.context

import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.nelurea.muninn.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Lossless, read-only capture of the user-facing Context graph at Room schema v23.
 *
 * Column values use SQLite storage-class prefixes (i/f/s/b) instead of JSON numbers,
 * so 64-bit row IDs, timestamps, and floating point values round-trip exactly.
 * Local row IDs are *snapshot-local* references; durable_identities supplies the
 * long-lived identity for Session, Work, Media, legacy Image, and Focus. An importer
 * must remap all local foreign keys rather than assuming those IDs can be reused.
 *
 * No media contents, authentication credentials, queues, or operational journals
 * are exported. Image/Media URI strings are metadata, not portable image files.
 */
class RoomContextSnapshotExporter(private val database: AppDatabase) {
    fun export(): ByteArray {
        val sql = database.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            if (sql.version != ROOM_VERSION) {
                throw IOException("Unsupported Context Room version ${sql.version}; expected $ROOM_VERSION")
            }
            assertStableIdentities(sql)
            val tables = JSONObject()
            for (table in TABLE_NAMES) {
                val columns = JSONArray()
                val rows = JSONArray()
                sql.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                    for (name in cursor.columnNames) columns.put(name)
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        for (index in 0 until cursor.columnCount) {
                            row.put(encodeCell(cursor, index))
                        }
                        rows.put(row)
                    }
                }
                tables.put(table, JSONObject().put("columns", columns).put("rows", rows))
            }
            val archive = JSONObject()
                .put("format", FORMAT)
                .put("roomVersion", ROOM_VERSION)
                .put("tables", tables)
            val bytes = archive.toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_SNAPSHOT_BYTES) throw IOException("Context export exceeds 64 MiB")
            validate(bytes)
            sql.setTransactionSuccessful()
            return bytes
        } finally {
            sql.endTransaction()
        }
    }

    private fun assertStableIdentities(sql: SupportSQLiteDatabase) {
        for ((type, table) in STABLE_TABLES) {
            val missing = sql.query(
                "SELECT COUNT(*) FROM `$table` t LEFT JOIN `durable_identities` d " +
                    "ON d.entityType = '$type' AND d.rowId = t.id WHERE d.stableId IS NULL"
            ).use { cursor ->
                if (!cursor.moveToFirst()) throw IOException("Identity verification failed for $table")
                cursor.getLong(0)
            }
            if (missing != 0L) throw IOException("Missing durable identities for $table: $missing")
        }
    }

    private fun encodeCell(cursor: Cursor, index: Int): Any = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> "i:${cursor.getLong(index)}"
        Cursor.FIELD_TYPE_FLOAT -> "f:${java.lang.Double.toString(cursor.getDouble(index))}"
        Cursor.FIELD_TYPE_STRING -> "s:${cursor.getString(index)}"
        Cursor.FIELD_TYPE_BLOB -> "b:${Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP)}"
        else -> throw IOException("Unexpected SQLite field type")
    }

    companion object {
        const val FORMAT = "muninn-context-room23-v1"
        private const val ROOM_VERSION = 23
        private const val MAX_SNAPSHOT_BYTES = 64 * 1024 * 1024

        /** An explicit allowlist prevents silently omitting a new Context table. */
        val TABLE_NAMES: List<String> = listOf(
            "sessions", "captured_works", "captured_media", "images", "captured_tags",
            "purpose_vocabulary", "session_purposes", "captured_work_purposes",
            "attraction_vocabulary", "captured_work_attractions", "captured_media_attractions",
            "aesthetic_response_vocabulary", "captured_work_responses", "media_focus",
            "state_vocabulary", "session_states", "save_events", "save_event_media",
            "durable_identities"
        )

        private val STABLE_TABLES: List<Pair<String, String>> = listOf(
            "session" to "sessions", "work" to "captured_works",
            "media" to "captured_media", "legacy_image" to "images", "focus" to "media_focus"
        )

        /** Reject unsupported or truncated formats before announcing a successful export. */
        fun validate(bytes: ByteArray): Map<String, Int> {
            if (bytes.size > MAX_SNAPSHOT_BYTES) throw IOException("Context archive is too large")
            val root = try { JSONObject(String(bytes, Charsets.UTF_8)) }
                catch (exception: Exception) { throw IOException("Invalid Context JSON", exception) }
            if (root.optString("format") != FORMAT || root.optInt("roomVersion") != ROOM_VERSION) {
                throw IOException("Unsupported Context archive format")
            }
            val tables = root.optJSONObject("tables") ?: throw IOException("Missing Context tables")
            val names = mutableSetOf<String>()
            val keys = tables.keys()
            while (keys.hasNext()) names.add(keys.next())
            if (names != TABLE_NAMES.toSet()) throw IOException("Context table inventory mismatch")
            val counts = linkedMapOf<String, Int>()
            for (table in TABLE_NAMES) {
                val content = tables.optJSONObject(table) ?: throw IOException("Missing $table")
                val columns = content.optJSONArray("columns") ?: throw IOException("Missing columns in $table")
                val rows = content.optJSONArray("rows") ?: throw IOException("Missing rows in $table")
                if (columns.length() == 0) throw IOException("Empty column list for $table")
                val unique = mutableSetOf<String>()
                for (c in 0 until columns.length()) {
                    val name = columns.optString(c)
                    if (name.isBlank() || !unique.add(name)) throw IOException("Invalid column in $table")
                }
                for (r in 0 until rows.length()) {
                    val row = rows.optJSONArray(r) ?: throw IOException("Invalid row in $table")
                    if (row.length() != columns.length()) throw IOException("Truncated Context row in $table")
                    for (c in 0 until row.length()) {
                        if (row.isNull(c)) continue
                        val value = row.opt(c) as? String ?: throw IOException("Unsupported cell in $table")
                        if (value.length < 2 || value[1] != ':' || value[0] !in "ifsb") {
                            throw IOException("Invalid cell encoding in $table")
                        }
                    }
                }
                counts[table] = rows.length()
            }
            return counts
        }
    }
}
