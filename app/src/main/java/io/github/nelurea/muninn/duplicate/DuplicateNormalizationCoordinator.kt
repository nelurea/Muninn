package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.DuplicateNormalizationDao
import io.github.nelurea.muninn.data.db.DuplicateNormalizationApplyResult
import io.github.nelurea.muninn.data.db.DuplicateNormalizationJournalEntity
import io.github.nelurea.muninn.data.db.DuplicateNormalizationState
import io.github.nelurea.muninn.data.db.DuplicateVerification
import io.github.nelurea.muninn.data.db.DuplicateVerificationDetails
import io.github.nelurea.muninn.data.db.DuplicateVerificationState
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

fun interface DuplicateMediaReader {
    fun open(localUri: String): InputStream?
}

class DuplicateNormalizationCoordinator(
    private val dao: DuplicateNormalizationDao,
    private val mediaReader: DuplicateMediaReader,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun planAll() = planAll(retryRejected = false)

    suspend fun retryRejected() = planAll(retryRejected = true)

    suspend fun normalizeAll() = normalizeAll(retryFailed = false)

    suspend fun retryFailedNormalization() = normalizeAll(retryFailed = true)

    private suspend fun normalizeAll(retryFailed: Boolean) {
        dao.getPlanned(plannedErrorFilter(retryFailed)).forEach { journal ->
            val plan = try {
                DuplicateNormalizationPlan.parse(requireNotNull(journal.planJson))
            } catch (_: Exception) {
                dao.recordNormalizationError(journal.id, DuplicateVerificationDetails.INVALID_PLAN, now())
                return@forEach
            }
            val verification = DuplicatePlanVerifier.verify(plan, mediaReader)
            if (verification != null) {
                dao.recordNormalizationError(journal.id, verification, now())
                return@forEach
            }
            try {
                normalizationErrorFor(dao.applyPlan(journal.id, plan, now()))?.let { error ->
                    dao.recordNormalizationError(journal.id, error, now())
                }
            } catch (_: Exception) {
                dao.recordNormalizationError(journal.id, DuplicateVerificationDetails.DB_APPLY_FAILED, now())
            }
        }
    }

    private suspend fun planAll(retryRejected: Boolean) {
        dao.getDuplicateIdentities().forEach { identity ->
            val journal = dao.ensureJournal(identity.sourceType, identity.sourceId, now())
            if (!shouldVerify(journal, retryRejected)) return@forEach
            val snapshot = dao.snapshot(identity.sourceType, identity.sourceId)
            if (snapshot.works.size < 2) {
                dao.finalizeVerification(journal.id, snapshot, DuplicateVerification.rejected("IDENTITY_NO_LONGER_DUPLICATE"), 1, null, null, now())
                return@forEach
            }
            if (snapshot.activeMoves.isNotEmpty()) {
                dao.finalizeVerification(journal.id, snapshot, DuplicateVerification.rejected(DuplicateVerificationDetails.MEDIA_MOVE_IN_PROGRESS), 1, null, null, now())
                return@forEach
            }
            when (val result = DuplicateMediaVerifier.verify(snapshot.media, mediaReader)) {
                is MediaVerificationResult.Rejected -> {
                    dao.finalizeVerification(journal.id, snapshot, DuplicateVerification.rejected(result.reason), 1, null, null, now())
                }
                is MediaVerificationResult.Verified -> {
                    val plan = DuplicateNormalizationPlanner.create(
                        identity.sourceType,
                        identity.sourceId,
                        snapshot,
                        result.media
                    )
                    dao.finalizeVerification(journal.id, snapshot, DuplicateVerification.verified(), DUPLICATE_PLAN_VERSION, plan.json, plan.canonicalWorkId, now())
                }
            }
        }
    }
}

internal fun plannedErrorFilter(retryFailed: Boolean): Boolean = retryFailed

internal fun normalizationErrorFor(result: DuplicateNormalizationApplyResult): String? = when (result) {
    DuplicateNormalizationApplyResult.APPLIED -> null
    DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED -> DuplicateVerificationDetails.SNAPSHOT_CHANGED
    DuplicateNormalizationApplyResult.MEDIA_MOVE_IN_PROGRESS -> DuplicateVerificationDetails.MEDIA_MOVE_IN_PROGRESS
}

data class DuplicatePlanMediaSnapshot(
    val id: Long,
    val workId: Long,
    val mediaIndex: Int,
    val localUri: String,
    val byteCount: Long,
    val sha256: String
)

data class DuplicatePlanMediaGroup(
    val mediaIndex: Int,
    val canonicalMediaId: Long,
    val duplicateMediaIds: List<Long>
)

data class DuplicateNormalizationPlan(
    val sourceType: String,
    val sourceId: String,
    val canonicalWorkId: Long,
    val duplicateWorkIds: List<Long>,
    val media: List<DuplicatePlanMediaGroup>,
    val mediaSnapshot: List<DuplicatePlanMediaSnapshot>
) {
    companion object {
        fun parse(json: String): DuplicateNormalizationPlan {
            val root = JSONObject(json)
            require(root.getInt("version") == DUPLICATE_PLAN_VERSION)
            fun longs(name: String) = root.getJSONArray(name).let { array ->
                (0 until array.length()).map(array::getLong)
            }
            val groups = root.getJSONArray("media").let { array ->
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    val duplicates = item.getJSONArray("duplicateMediaIds")
                    DuplicatePlanMediaGroup(
                        item.getInt("mediaIndex"),
                        item.getLong("canonicalMediaId"),
                        (0 until duplicates.length()).map(duplicates::getLong)
                    )
                }
            }
            val snapshot = root.getJSONArray("mediaSnapshot").let { array ->
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    DuplicatePlanMediaSnapshot(
                        item.getLong("id"), item.getLong("workId"), item.getInt("mediaIndex"),
                        item.getString("localUri"), item.getLong("byteCount"), item.getString("sha256")
                    )
                }
            }
            return DuplicateNormalizationPlan(
                root.getString("sourceType"), root.getString("sourceId"),
                root.getLong("canonicalWorkId"), longs("duplicateWorkIds"), groups, snapshot
            ).also { plan ->
                val plannedIds = plan.media.flatMap { listOf(it.canonicalMediaId) + it.duplicateMediaIds }.toSet()
                require(plannedIds == plan.mediaSnapshot.map { it.id }.toSet())
            }
        }
    }
}

object DuplicatePlanVerifier {
    fun verify(plan: DuplicateNormalizationPlan, reader: DuplicateMediaReader): String? {
        for (item in plan.mediaSnapshot) {
            val actual = DuplicateMediaVerifier.verifyOne(item.id, item.localUri, reader)
                ?: return DuplicateVerificationDetails.UNREADABLE
            if (actual.byteCount != item.byteCount) return DuplicateVerificationDetails.SIZE_MISMATCH
            if (actual.sha256 != item.sha256) return DuplicateVerificationDetails.HASH_MISMATCH
        }
        return null
    }
}

internal fun shouldVerify(journal: DuplicateNormalizationJournalEntity, retryRejected: Boolean): Boolean =
    journal.state == DuplicateNormalizationState.PENDING &&
        (journal.verificationState != DuplicateVerificationState.REJECTED || retryRejected)

sealed class MediaVerificationResult {
    data class Verified(val media: List<VerifiedMedia>) : MediaVerificationResult()
    data class Rejected(val reason: String) : MediaVerificationResult()
}

object DuplicateMediaVerifier {
    fun verify(media: List<CapturedMediaEntity>, mediaReader: DuplicateMediaReader): MediaVerificationResult {
        val results = mutableListOf<VerifiedMedia>()
        for (item in media) {
            val verified = verifyOne(item.id, item.localUri, mediaReader)
            if (verified == null) {
                return MediaVerificationResult.Rejected(DuplicateVerificationDetails.UNREADABLE)
            }
            results += verified
        }
        val byId = media.associateBy { it.id }
        val groups = results.groupBy { byId.getValue(it.mediaId).mediaIndex }.values
        if (groups.any { candidates -> candidates.map { it.byteCount }.distinct().size > 1 }) {
            return MediaVerificationResult.Rejected(DuplicateVerificationDetails.SIZE_MISMATCH)
        }
        if (groups.any { candidates -> candidates.map { it.sha256 }.distinct().size > 1 }) {
            return MediaVerificationResult.Rejected(DuplicateVerificationDetails.HASH_MISMATCH)
        }
        return MediaVerificationResult.Verified(results)
    }

    internal fun verifyOne(mediaId: Long, localUri: String, mediaReader: DuplicateMediaReader): VerifiedMedia? {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        return try {
            val input = mediaReader.open(localUri) ?: return null
            input.use {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    digest.update(buffer, 0, read)
                    count += read
                }
            }
            VerifiedMedia(mediaId, count, digest.digest().joinToString("") { "%02x".format(it) })
        } catch (_: Exception) {
            null
        }
    }
}
