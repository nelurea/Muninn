package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.DuplicateNormalizationDao
import io.github.nelurea.muninn.data.db.DuplicateNormalizationJournalEntity
import io.github.nelurea.muninn.data.db.DuplicateNormalizationState
import io.github.nelurea.muninn.data.db.DuplicateVerification
import io.github.nelurea.muninn.data.db.DuplicateVerificationDetails
import io.github.nelurea.muninn.data.db.DuplicateVerificationState
import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import java.io.InputStream
import java.security.MessageDigest

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
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            try {
                val input = mediaReader.open(item.localUri)
                    ?: return MediaVerificationResult.Rejected(DuplicateVerificationDetails.UNREADABLE)
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
            } catch (_: Exception) {
                return MediaVerificationResult.Rejected(DuplicateVerificationDetails.UNREADABLE)
            }
            results += VerifiedMedia(item.id, count, digest.digest().joinToString("") { "%02x".format(it) })
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
}
