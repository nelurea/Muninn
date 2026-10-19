package io.github.nelurea.muninn.data.context

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.installDurableIdentityTriggers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** In-memory fixtures only. Never opens muninn.db or the real user's SAF directory. */
@RunWith(AndroidJUnit4::class)
class RoomContextSnapshotExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun exportsAllContextTablesAndPreservesIdentityAndExactValues() {
        val database = newDatabase()
        try {
            populate(database.openHelper.writableDatabase)
            val bytes = RoomContextSnapshotExporter(database).export()
            val counts = RoomContextSnapshotExporter.validate(bytes)
            assertEquals(RoomContextSnapshotExporter.TABLE_NAMES.toSet(), counts.keys)
            assertEquals(1, counts["sessions"])
            assertEquals(1, counts["captured_works"])
            assertEquals(1, counts["captured_media"])
            assertEquals(1, counts["media_focus"])
            assertEquals(1, counts["session_purposes"])
            assertEquals(1, counts["captured_work_purposes"])
            assertEquals(1, counts["captured_tags"])
            assertEquals(5, counts["durable_identities"])
            val tables = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONObject("tables")
            assertEquals("s:content://saved/image-one", cell(tables, "captured_media", "localUri"))
            assertEquals("s:known-image", cell(tables, "images", "imageUri"))
            assertEquals("i:7", cell(tables, "captured_works", "sessionId"))
            assertEquals("i:20", cell(tables, "media_focus", "mediaId"))
            assertEquals("s:Reference", cell(tables, "purpose_vocabulary", "label"))
            assertEquals("i:9223372036854775000", cell(tables, "sessions", "createdAt"))
            assertTrue(bytes.size < 64 * 1024 * 1024)
        } finally {
            database.close()
        }
    }

    @Test fun refusesAnIncompleteIdentityMapRatherThanExportingBrokenRelationships() {
        val database = newDatabase()
        try {
            populate(database.openHelper.writableDatabase)
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM durable_identities WHERE entityType = 'media' AND rowId = 20"
            )
            assertThrows(IOException::class.java) {
                RoomContextSnapshotExporter(database).export()
            }
        } finally {
            database.close()
        }
    }

    @Test fun initialBackupRefusesToShadowAlreadyCommittedExternalContext() = runBlocking {
        val database = newDatabase()
        try {
            populate(database.openHelper.writableDatabase)
            val store = MemoryDocumentStore()
            val service = ContextInitialBackupService(database, store)
            val first = service.backupOnce()
            assertEquals(32, first.generation.length)
            val prior = requireNotNull(VerifiedContextJournal(store).readLatest())
            assertEquals(1, RoomContextSnapshotExporter.validate(prior.bytes)["sessions"])
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO sessions (id, createdAt, lastActivityAt) VALUES (8, 8, 8)"
            )
            var refused = false
            try {
                service.backupOnce()
            } catch (_: IOException) {
                refused = true
            }
            assertTrue("Existing committed Context must not be overwritten", refused)
            assertEquals(first, requireNotNull(VerifiedContextJournal(store).readLatest()).receipt)
            assertEquals(2, store.entries.size)
        } finally {
            database.close()
        }
    }

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    installDurableIdentityTriggers(db)
                }
            })
            .build()

    private fun populate(sql: SupportSQLiteDatabase) {
        sql.execSQL("INSERT INTO sessions (id,createdAt,lastActivityAt) VALUES (7,9223372036854775000,100)")
        sql.execSQL(
            """INSERT INTO captured_works
                (id,sourceType,sourceId,canonicalUrl,capturedAt,authorId,authorName,title,caption,contentRestriction,sessionId)
                VALUES (10,'pixiv','123','https://example.invalid/123','2026-01-01','author','A','Work','Caption','UNKNOWN',7)
            """.trimIndent()
        )
        sql.execSQL(
            """INSERT INTO captured_media
                (id,workId,mediaIndex,localUri,sourceUrl,mimeType,fileName,isHighlighted)
                VALUES (20,10,0,'content://saved/image-one','https://example.invalid/media','image/jpeg','one.jpg',1)
            """.trimIndent()
        )
        sql.execSQL("INSERT INTO images (id,imageUri,createdAt,sessionId) VALUES (30,'known-image',200,7)")
        sql.execSQL("INSERT INTO purpose_vocabulary (id,label,lastUsedAt) VALUES (9,'Reference',50)")
        sql.execSQL("INSERT INTO session_purposes (sessionId,purposeVocabularyId) VALUES (7,9)")
        sql.execSQL("INSERT INTO captured_work_purposes (workId,purposeVocabularyId) VALUES (10,9)")
        sql.execSQL("INSERT INTO captured_tags (workId,position,tag) VALUES (10,0,'sunset')")
        sql.execSQL("INSERT INTO media_focus (id,mediaId,attractionVocabularyId,note) VALUES (40,20,NULL,'detail')")
    }

    private fun cell(tables: JSONObject, table: String, column: String): String {
        val entry = tables.getJSONObject(table)
        val columns = entry.getJSONArray("columns")
        val index = (0 until columns.length()).first { columns.getString(it) == column }
        return entry.getJSONArray("rows").getJSONArray(0).getString(index)
    }

    private class MemoryDocumentStore : ContextDocumentStore {
        val entries = linkedMapOf<String, ByteArray>()
        override fun list(): List<ContextDocument> = entries.keys.map { ContextDocument(it, it) }
        override fun create(displayName: String, mimeType: String): ContextDocument {
            if (entries.containsKey(displayName)) throw IOException("Document already exists")
            entries[displayName] = byteArrayOf()
            return ContextDocument(displayName, displayName)
        }
        override fun write(document: ContextDocument, bytes: ByteArray) {
            if (!entries.containsKey(document.uri)) throw IOException("Missing document")
            entries[document.uri] = bytes.copyOf()
        }
        override fun read(document: ContextDocument): ByteArray =
            entries[document.uri]?.copyOf() ?: throw IOException("Missing document")
    }
}
