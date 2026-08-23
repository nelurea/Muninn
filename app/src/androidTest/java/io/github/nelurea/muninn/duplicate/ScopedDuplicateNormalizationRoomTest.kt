package io.github.nelurea.muninn.duplicate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.data.db.AppDatabase
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScopedDuplicateNormalizationRoomTest {
    private lateinit var database: AppDatabase
    private lateinit var content: Map<String, ByteArray>
    private val identities = (1..8).map { DuplicateIdentityKey("pixiv", "verified-$it") } +
        DuplicateIdentityKey("pixiv", "rejected")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        content = insertNineIdentitiesWithTwentyEightMediaIndexGroups()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun dryRunObservesAllDuplicatesWithoutCreatingJournals() = runBlocking {
        val manifest = scoped().dryRun(identities)

        val json = JSONObject(manifest.json)
        val results = json.getJSONArray("identities")
        assertEquals(9, results.length())
        assertEquals(8, (0 until results.length()).count { results.getJSONObject(it).getString("status") == "VERIFIED" })
        val rejected = (0 until results.length()).map(results::getJSONObject)
            .single { it.getString("sourceId") == "rejected" }
        assertEquals("HASH_MISMATCH", rejected.getString("details"))
        assertEquals(28, (0 until results.length()).sumOf { results.getJSONObject(it).getJSONArray("media").length() / 2 })
        assertEquals(0, count("duplicate_normalization_journal"))
        assertEquals(0, count("duplicate_cleanup_journal"))
    }

    @Test
    fun applyCompletesOnlyVerifiedThenRerunIsNormalizationNoOpAndRetriesCleanup() = runBlocking {
        var cleanupCanComplete = false
        val runner = scoped(DuplicateCleanupDeleter {
            if (cleanupCanComplete) DuplicateCleanupDeleteResult.Missing
            else DuplicateCleanupDeleteResult.Failed("TEST_RETRY")
        })
        val manifest = runner.dryRun(identities)

        val first = runner.apply(identities, manifest.fingerprint)
        assertEquals(8, first.normalized)
        assertEquals(8, first.completed)
        assertEquals(8, countWhere("duplicate_normalization_journal", "state='COMPLETED'"))
        assertEquals(0, countWhere("duplicate_normalization_journal", "verificationState='REJECTED'"))
        assertEquals(0, countWhere("duplicate_normalization_journal", "sourceId='rejected'"))
        assertEquals(1, duplicateCount())
        assertEquals(24, first.cleanupPending)

        cleanupCanComplete = true
        val second = runner.apply(identities, manifest.fingerprint)
        assertEquals(0, second.normalized)
        assertEquals(8, second.completed)
        assertEquals(0, second.cleanupPending)
        assertEquals(1, duplicateCount())
        assertEquals(0, countWhere("duplicate_normalization_journal", "sourceId='rejected'"))
    }

    @Test
    fun applyRejectsCleanupJournalThatDoesNotBelongToCompletedScopedNormalization() = runBlocking {
        val manifest = scoped().dryRun(identities)
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO duplicate_normalization_journal " +
                "(id,sourceType,sourceId,state,verificationState,createdAt,updatedAt) " +
                "VALUES (1,'pixiv','verified-1','PENDING','UNKNOWN',1,1)"
        )
        sql.execSQL(
            "INSERT INTO duplicate_cleanup_journal " +
                "(normalizationId,targetUri,state,updatedAt) VALUES (1,'unrelated','PENDING',1)"
        )

        val failure = runCatching { scoped().apply(identities, manifest.fingerprint) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("UNEXPECTED_CLEANUP_JOURNAL", failure?.message)
        assertEquals(1, count("duplicate_normalization_journal"))
    }

    private fun scoped(
        deleter: DuplicateCleanupDeleter = DuplicateCleanupDeleter { DuplicateCleanupDeleteResult.Missing }
    ) = ScopedDuplicateNormalization(
        database.duplicateNormalizationDao(),
        DuplicateMediaReader { content[it]?.let(::ByteArrayInputStream) },
        DuplicateCleanupService(
            RoomDuplicateCleanupPersistence(database),
            deleter,
            ioDispatcher = Dispatchers.Unconfined
        ),
        now = { 100L }
    )

    private fun insertNineIdentitiesWithTwentyEightMediaIndexGroups(): Map<String, ByteArray> {
        val bytes = mutableMapOf<String, ByteArray>()
        identities.forEachIndexed { index, identity ->
            val groupCount = if (identity.sourceId == "rejected") 4 else 3
            insertDuplicate(identity, index + 1, groupCount, bytes, mismatchIndex = if (identity.sourceId == "rejected") 2 else null)
        }
        return bytes
    }

    private fun insertDuplicate(
        identity: DuplicateIdentityKey,
        ordinal: Int,
        groupCount: Int,
        bytes: MutableMap<String, ByteArray>,
        mismatchIndex: Int?
    ) {
        val sql = database.openHelper.writableDatabase
        val firstWork = ordinal * 10L
        val secondWork = firstWork + 1
        sql.execSQL(
            "INSERT INTO captured_works VALUES (?,? ,?,'url','2026-01-01',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)",
            arrayOf(firstWork, identity.sourceType, identity.sourceId)
        )
        sql.execSQL(
            "INSERT INTO captured_works VALUES (?,? ,?,'url','2026-01-02',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)",
            arrayOf(secondWork, identity.sourceType, identity.sourceId)
        )
        repeat(groupCount) { mediaIndex ->
            val firstMedia = ordinal * 100L + mediaIndex * 2
            val secondMedia = firstMedia + 1
            val firstUri = "memory://${identity.sourceId}/$mediaIndex/first"
            val secondUri = "memory://${identity.sourceId}/$mediaIndex/second"
            sql.execSQL(
                "INSERT INTO captured_media VALUES (?, ?,?,?,?,'image/jpeg','first.jpg',0)",
                arrayOf(firstMedia, firstWork, mediaIndex, firstUri, "")
            )
            sql.execSQL(
                "INSERT INTO captured_media VALUES (?, ?,?,?,?,'image/jpeg','second.jpg',0)",
                arrayOf(secondMedia, secondWork, mediaIndex, secondUri, "")
            )
            val payload = byteArrayOf(ordinal.toByte(), mediaIndex.toByte(), 42)
            bytes[firstUri] = payload
            bytes[secondUri] = if (mediaIndex == mismatchIndex) payload.copyOf().also { it[2] = 43 } else payload
        }
    }

    private fun count(table: String) = scalar("SELECT COUNT(*) FROM $table")

    private fun countWhere(table: String, where: String) = scalar("SELECT COUNT(*) FROM $table WHERE $where")

    private fun duplicateCount() = scalar(
        "SELECT COUNT(*) FROM (SELECT sourceType,sourceId FROM captured_works " +
            "GROUP BY sourceType,sourceId HAVING COUNT(*) > 1)"
    )

    private fun scalar(query: String): Int = database.openHelper.writableDatabase.query(query).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}
