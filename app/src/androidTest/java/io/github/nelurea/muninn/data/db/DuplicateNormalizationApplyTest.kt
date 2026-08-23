package io.github.nelurea.muninn.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nelurea.muninn.duplicate.DuplicateNormalizationPlan
import io.github.nelurea.muninn.duplicate.DuplicatePlanMediaGroup
import io.github.nelurea.muninn.duplicate.DuplicatePlanMediaSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicateNormalizationApplyTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun appliesIdentityAtomicallyAndExcludesSharedUriFromCleanup() = runBlocking {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO sessions VALUES (7,1,1)")
        sql.execSQL("INSERT INTO captured_works VALUES (10,'pixiv','x','','2026-01-01',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)")
        sql.execSQL("INSERT INTO captured_works VALUES (20,'pixiv','x','url','2026-01-02','published','mode','query','a','name','handle','title','caption',7)")
        sql.execSQL("INSERT INTO captured_works VALUES (30,'pixiv','other','other','2026-01-03',NULL,NULL,NULL,'b','other',NULL,NULL,'',NULL)")
        sql.execSQL("INSERT INTO captured_media VALUES (101,10,0,'shared','s','image/jpeg','a.jpg',0)")
        sql.execSQL("INSERT INTO captured_media VALUES (202,20,0,'shared','s','image/jpeg','b.jpg',1)")
        sql.execSQL("INSERT INTO captured_media VALUES (203,20,1,'missing-index','s2','image/jpeg','c.jpg',0)")
        sql.execSQL("INSERT INTO captured_media VALUES (102,10,2,'kept','s3','image/jpeg','e.jpg',0)")
        sql.execSQL("INSERT INTO captured_media VALUES (204,20,2,'delete-me','s3','image/jpeg','f.jpg',0)")
        sql.execSQL("INSERT INTO captured_media VALUES (303,30,0,'shared','s','image/jpeg','d.jpg',0)")
        sql.execSQL("INSERT INTO captured_tags VALUES (10,0,'one')")
        sql.execSQL("INSERT INTO captured_tags VALUES (20,0,'two')")
        sql.execSQL("INSERT INTO purpose_vocabulary VALUES (1,'reference')")
        sql.execSQL("INSERT INTO attraction_vocabulary VALUES (1,'subject','face')")
        sql.execSQL("INSERT INTO captured_work_purposes VALUES (20,1)")
        sql.execSQL("INSERT INTO captured_work_attractions VALUES (20,1)")
        sql.execSQL("INSERT INTO captured_media_attractions VALUES (202,1)")
        sql.execSQL("INSERT INTO media_focus VALUES (1,202,1,'focus',NULL,NULL,NULL,NULL)")
        sql.execSQL("INSERT INTO save_events VALUES (50,'pixiv','x','url','2026-01-02','CAPTURE',NULL,7,20,'mode','query','RESAVE')")
        sql.execSQL("INSERT INTO save_event_media VALUES (60,50,202,0,'shared','s','image/jpeg','b.jpg',1,1,0,0)")
        sql.execSQL("INSERT INTO duplicate_normalization_journal VALUES (1,'pixiv','x','PLANNED','VERIFIED',NULL,10,2,'{}',NULL,1,1)")
        val plan = DuplicateNormalizationPlan(
            "pixiv", "x", 10, listOf(20),
            listOf(
                DuplicatePlanMediaGroup(0, 101, listOf(202)),
                DuplicatePlanMediaGroup(1, 203, emptyList()),
                DuplicatePlanMediaGroup(2, 102, listOf(204))
            ),
            listOf(
                DuplicatePlanMediaSnapshot(101, 10, 0, "shared", 1, "a"),
                DuplicatePlanMediaSnapshot(202, 20, 0, "shared", 1, "a"),
                DuplicatePlanMediaSnapshot(203, 20, 1, "missing-index", 1, "b"),
                DuplicatePlanMediaSnapshot(102, 10, 2, "kept", 1, "c"),
                DuplicatePlanMediaSnapshot(204, 20, 2, "delete-me", 1, "c")
            )
        )

        assertEquals(DuplicateNormalizationApplyResult.APPLIED, database.duplicateNormalizationDao().applyPlan(1, plan, 2))
        assertNull(database.capturedWorkDao().getWithMediaById(20))
        val canonical = database.capturedWorkDao().getWithMediaById(10)
        assertNotNull(canonical)
        assertEquals("url", canonical!!.work.canonicalUrl)
        assertEquals("published", canonical.work.publishedAt)
        assertEquals("mode", canonical.work.discoveryMode)
        assertEquals("handle", canonical.work.authorHandle)
        assertEquals("title", canonical.work.title)
        assertNull(canonical.work.sessionId)
        assertEquals(true, canonical.media.first { it.id == 101L }.isHighlighted)
        assertEquals(10, scalar(sql, "SELECT workId FROM captured_media WHERE id=203"))
        assertEquals(listOf("one", "two"), canonical.tags.map { it.tag })
        assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM save_events WHERE saveKind='LEGACY'"))
        assertEquals(10, scalar(sql, "SELECT canonicalWorkId FROM save_events WHERE id=50"))
        assertEquals(101, scalar(sql, "SELECT capturedMediaId FROM save_event_media WHERE id=60"))
        assertEquals(10, scalar(sql, "SELECT workId FROM captured_work_purposes WHERE purposeVocabularyId=1"))
        assertEquals(10, scalar(sql, "SELECT workId FROM captured_work_attractions WHERE attractionVocabularyId=1"))
        assertEquals(101, scalar(sql, "SELECT mediaId FROM media_focus WHERE id=1"))
        assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM captured_media_attractions WHERE mediaId=101 AND attractionVocabularyId=1"))
        assertEquals(0, scalar(sql, "SELECT COUNT(*) FROM duplicate_cleanup_journal WHERE targetUri='shared'"))
        assertEquals(1, scalar(sql, "SELECT COUNT(*) FROM duplicate_cleanup_journal WHERE targetUri='delete-me'"))
        assertEquals("COMPLETED", text(sql, "SELECT state FROM duplicate_normalization_journal WHERE id=1"))
    }

    @Test
    fun reportsSnapshotChangedWithoutApplyingPlan() = runBlocking {
        val sql = database.openHelper.writableDatabase
        insertMinimalPlannedIdentity(sql)
        val plan = minimalPlan().copy(duplicateWorkIds = listOf(20, 30))

        assertEquals(
            DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED,
            database.duplicateNormalizationDao().applyPlan(1, plan, 2)
        )
        assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM captured_works WHERE sourceType='pixiv' AND sourceId='x'"))
        assertEquals("PLANNED", text(sql, "SELECT state FROM duplicate_normalization_journal WHERE id=1"))
    }

    @Test
    fun reportsMediaMoveInProgressWithoutCollapsingItIntoSnapshotChanged() = runBlocking {
        val sql = database.openHelper.writableDatabase
        insertMinimalPlannedIdentity(sql)
        sql.execSQL(
            "INSERT INTO media_move_journal " +
                "(id,mediaId,sourceUri,destinationRootUri,destinationUri,state,byteCount,lastError,updatedAt) " +
                "VALUES (1,101,'same','content://target',NULL,'PENDING',NULL,NULL,1)"
        )

        assertEquals(
            DuplicateNormalizationApplyResult.MEDIA_MOVE_IN_PROGRESS,
            database.duplicateNormalizationDao().applyPlan(1, minimalPlan(), 2)
        )
        assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM captured_works WHERE sourceType='pixiv' AND sourceId='x'"))
        assertEquals("PLANNED", text(sql, "SELECT state FROM duplicate_normalization_journal WHERE id=1"))
    }

    @Test
    fun completionCasFailureRollsBackWholeIdentityTransaction() = runBlocking {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO captured_works VALUES (10,'pixiv','x','canonical','2026-01-01',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)")
        sql.execSQL("INSERT INTO captured_works VALUES (20,'pixiv','x','duplicate','2026-01-02',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)")
        sql.execSQL("INSERT INTO captured_media VALUES (101,10,0,'same','s','image/jpeg','a.jpg',0)")
        sql.execSQL("INSERT INTO captured_media VALUES (202,20,0,'same','s','image/jpeg','b.jpg',1)")
        sql.execSQL("INSERT INTO duplicate_normalization_journal VALUES (1,'pixiv','x','PLANNED','VERIFIED',NULL,10,2,'{}',NULL,1,1)")
        sql.execSQL("CREATE TRIGGER fail_completion AFTER DELETE ON captured_works BEGIN UPDATE duplicate_normalization_journal SET state='PENDING' WHERE id=1; END")
        val plan = DuplicateNormalizationPlan(
            "pixiv", "x", 10, listOf(20),
            listOf(DuplicatePlanMediaGroup(0, 101, listOf(202))),
            listOf(
                DuplicatePlanMediaSnapshot(101, 10, 0, "same", 1, "a"),
                DuplicatePlanMediaSnapshot(202, 20, 0, "same", 1, "a")
            )
        )

        try {
            database.duplicateNormalizationDao().applyPlan(1, plan, 2)
            fail("completion CAS failure must throw")
        } catch (_: IllegalStateException) {
            // Expected: Room rolls the complete apply transaction back.
        }

        assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM captured_works WHERE sourceType='pixiv' AND sourceId='x'"))
        assertEquals(2, scalar(sql, "SELECT COUNT(*) FROM captured_media WHERE id IN (101,202)"))
        assertEquals(0, scalar(sql, "SELECT COUNT(*) FROM save_events"))
        assertEquals(0, scalar(sql, "SELECT COUNT(*) FROM duplicate_cleanup_journal"))
        assertEquals("PLANNED", text(sql, "SELECT state FROM duplicate_normalization_journal WHERE id=1"))
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String): Int =
        db.query(query).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun text(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String): String =
        db.query(query).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

    private fun insertMinimalPlannedIdentity(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO captured_works VALUES (10,'pixiv','x','canonical','2026-01-01',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)")
        db.execSQL("INSERT INTO captured_works VALUES (20,'pixiv','x','duplicate','2026-01-02',NULL,NULL,NULL,'a','name',NULL,NULL,'',NULL)")
        db.execSQL("INSERT INTO captured_media VALUES (101,10,0,'same','s','image/jpeg','a.jpg',0)")
        db.execSQL("INSERT INTO captured_media VALUES (202,20,0,'same','s','image/jpeg','b.jpg',1)")
        db.execSQL("INSERT INTO duplicate_normalization_journal VALUES (1,'pixiv','x','PLANNED','VERIFIED',NULL,10,2,'{}',NULL,1,1)")
    }

    private fun minimalPlan() = DuplicateNormalizationPlan(
        "pixiv", "x", 10, listOf(20),
        listOf(DuplicatePlanMediaGroup(0, 101, listOf(202))),
        listOf(
            DuplicatePlanMediaSnapshot(101, 10, 0, "same", 1, "a"),
            DuplicatePlanMediaSnapshot(202, 20, 0, "same", 1, "a")
        )
    )
}
