package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.CapturedWorkEntity
import io.github.nelurea.muninn.data.db.DuplicateIdentitySnapshot
import io.github.nelurea.muninn.data.db.DuplicateNormalizationJournalEntity
import io.github.nelurea.muninn.data.db.DuplicateNormalizationState
import io.github.nelurea.muninn.data.db.DuplicateVerificationDetails
import io.github.nelurea.muninn.data.db.DuplicateVerificationState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DuplicateNormalizationPlannerTest {
    @Test
    fun `canonical selection and plan json are deterministic`() {
        val snapshot = DuplicateIdentitySnapshot(
            works = listOf(work(20, "2026-01-02"), work(10, "2026-01-01")),
            media = listOf(media(202, 20, 0, "b"), media(101, 10, 0, "a"), media(203, 20, 1, "c")),
            activeMoves = emptyList()
        )
        val verified = listOf(
            VerifiedMedia(203, 1, "03"), VerifiedMedia(202, 1, "01"), VerifiedMedia(101, 1, "01")
        )

        val first = DuplicateNormalizationPlanner.create("pixiv", "source", snapshot, verified)
        val second = DuplicateNormalizationPlanner.create(
            "pixiv",
            "source",
            snapshot.copy(works = snapshot.works.reversed(), media = snapshot.media.reversed()),
            verified.reversed()
        )

        assertEquals(10, first.canonicalWorkId)
        assertEquals(first.json, second.json)
        val json = JSONObject(first.json)
        assertEquals(DUPLICATE_PLAN_VERSION, json.getInt("version"))
        assertEquals("pixiv", json.getString("sourceType"))
        assertEquals("source", json.getString("sourceId"))
        assertEquals(101, json.getJSONArray("media").getJSONObject(0).getLong("canonicalMediaId"))
        val mediaSnapshot = json.getJSONArray("mediaSnapshot")
        assertEquals(101, mediaSnapshot.getJSONObject(0).getLong("id"))
        assertEquals(10, mediaSnapshot.getJSONObject(0).getLong("workId"))
        assertEquals(0, mediaSnapshot.getJSONObject(0).getInt("mediaIndex"))
        assertEquals("a", mediaSnapshot.getJSONObject(0).getString("localUri"))
        assertEquals(1, mediaSnapshot.getJSONObject(0).getLong("byteCount"))
        assertEquals("01", mediaSnapshot.getJSONObject(0).getString("sha256"))
        assertEquals(listOf(101L, 202L, 203L), (0 until mediaSnapshot.length()).map { mediaSnapshot.getJSONObject(it).getLong("id") })
    }

    @Test
    fun `verification counts bytes hashes content and rejects mismatch`() {
        val candidates = listOf(media(1, 10, 0, "one"), media(2, 20, 0, "two"))
        val same = DuplicateMediaVerifier.verify(candidates) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            as MediaVerificationResult.Verified
        assertEquals(listOf(3L, 3L), same.media.map { it.byteCount })
        assertTrue(same.media.all { it.sha256 == "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81" })

        val sizeMismatch = DuplicateMediaVerifier.verify(candidates) { uri ->
            ByteArrayInputStream(if (uri == "one") byteArrayOf(1) else byteArrayOf(1, 2))
        } as MediaVerificationResult.Rejected
        assertEquals(DuplicateVerificationDetails.SIZE_MISMATCH, sizeMismatch.reason)

        val hashMismatch = DuplicateMediaVerifier.verify(candidates) { uri ->
            ByteArrayInputStream(if (uri == "one") byteArrayOf(1, 2) else byteArrayOf(2, 1))
        } as MediaVerificationResult.Rejected
        assertEquals(DuplicateVerificationDetails.HASH_MISMATCH, hashMismatch.reason)
    }

    @Test
    fun `verification rejects unreadable media`() {
        val result = DuplicateMediaVerifier.verify(listOf(media(1, 10, 0, "missing"))) { null }
            as MediaVerificationResult.Rejected
        assertEquals(DuplicateVerificationDetails.UNREADABLE, result.reason)
    }

    @Test
    fun `rejected pending journal is skipped normally and retried explicitly while planned is immutable`() {
        val rejected = journal(
            state = DuplicateNormalizationState.PENDING,
            verificationState = DuplicateVerificationState.REJECTED
        )
        assertFalse(shouldVerify(rejected, retryRejected = false))
        assertTrue(shouldVerify(rejected, retryRejected = true))

        val planned = journal(
            state = DuplicateNormalizationState.PLANNED,
            verificationState = DuplicateVerificationState.VERIFIED
        )
        assertFalse(shouldVerify(planned, retryRejected = false))
        assertFalse(shouldVerify(planned, retryRejected = true))
    }

    @Test
    fun `plan v2 parser and revalidation cover every snapshotted medium`() {
        val snapshot = DuplicateIdentitySnapshot(
            works = listOf(work(10, "2026-01-01"), work(20, "2026-01-02")),
            media = listOf(media(101, 10, 0, "a"), media(202, 20, 0, "b")),
            activeMoves = emptyList()
        )
        val bytes = byteArrayOf(1, 2, 3)
        val verified = DuplicateMediaVerifier.verify(snapshot.media) { ByteArrayInputStream(bytes) }
            as MediaVerificationResult.Verified
        val encoded = DuplicateNormalizationPlanner.create("pixiv", "source", snapshot, verified.media).json
        val plan = DuplicateNormalizationPlan.parse(encoded)
        val opened = mutableListOf<String>()

        assertEquals(null, DuplicatePlanVerifier.verify(plan) { uri ->
            opened += uri
            ByteArrayInputStream(bytes)
        })
        assertEquals(listOf("a", "b"), opened)
        assertEquals(DuplicateVerificationDetails.HASH_MISMATCH, DuplicatePlanVerifier.verify(plan) { uri ->
            ByteArrayInputStream(if (uri == "a") bytes else byteArrayOf(3, 2, 1))
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects non v2 plan`() {
        DuplicateNormalizationPlan.parse("""{"version":1}""")
    }

    private fun work(id: Long, capturedAt: String) = CapturedWorkEntity(
        id, "pixiv", "source", "url", capturedAt, null, null, null, "author", "name", null, null, "", null
    )

    private fun media(id: Long, workId: Long, index: Int, uri: String) = CapturedMediaEntity(
        id, workId, index, uri, "source", "image/jpeg", "file", false
    )

    private fun journal(state: String, verificationState: String) = DuplicateNormalizationJournalEntity(
        id = 1,
        sourceType = "pixiv",
        sourceId = "source",
        state = state,
        verificationState = verificationState,
        createdAt = 1,
        updatedAt = 1
    )
}
