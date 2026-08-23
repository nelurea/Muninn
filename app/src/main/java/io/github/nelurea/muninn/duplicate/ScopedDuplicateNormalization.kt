package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.DuplicateIdentitySnapshot
import io.github.nelurea.muninn.data.db.DuplicateNormalizationDao
import io.github.nelurea.muninn.data.db.DuplicateNormalizationState
import io.github.nelurea.muninn.data.db.DuplicateVerification
import io.github.nelurea.muninn.data.db.DuplicateVerificationDetails
import io.github.nelurea.muninn.data.db.DuplicateVerificationState
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class DuplicateIdentityKey(val sourceType: String, val sourceId: String)

data class ScopedDuplicateManifest(val json: String, val fingerprint: String)

data class ScopedDuplicateRunResult(
    val manifest: ScopedDuplicateManifest,
    val normalized: Int,
    val completed: Int,
    val cleanupPending: Int,
    val cleanupFailed: Int
)

internal fun requireStrictIdentityScope(identities: List<DuplicateIdentityKey>) {
    require(identities.isNotEmpty() && identities.distinct().size == identities.size) {
        "NON_EMPTY_UNIQUE_IDENTITY_SCOPE_REQUIRED"
    }
}

internal fun sha256Fingerprint(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal fun requireManifestFingerprint(expected: String, actual: String) {
    require(expected.matches(Regex("[0-9a-f]{64}")) && expected == actual) { "MANIFEST_FINGERPRINT_MISMATCH" }
}

internal fun shouldNormalize(state: String): Boolean = state != DuplicateNormalizationState.COMPLETED

internal fun shouldStartCleanup(verified: Int, completed: Int): Boolean = completed == verified

/** A deliberately narrow, fail-closed entry point for the one-off v18 repair. */
class ScopedDuplicateNormalization(
    private val dao: DuplicateNormalizationDao,
    private val mediaReader: DuplicateMediaReader,
    private val cleanup: DuplicateCleanupService,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun dryRun(identities: List<DuplicateIdentityKey>): ScopedDuplicateManifest = prepare(identities).manifest

    suspend fun apply(
        identities: List<DuplicateIdentityKey>,
        expectedFingerprint: String
    ): ScopedDuplicateRunResult {
        val prepared = prepare(identities)
        requireManifestFingerprint(expectedFingerprint, prepared.manifest.fingerprint)

        val scoped = identities.toSet()
        val verifiedScope = prepared.entries.filter {
            it.snapshot == null || it.verification is MediaVerificationResult.Verified
        }.map { it.identity }.toSet()
        val journals = dao.getAllJournals()
        require(journals.none { DuplicateIdentityKey(it.sourceType, it.sourceId) !in scoped }) { "UNEXPECTED_JOURNAL" }
        require(journals.all { DuplicateIdentityKey(it.sourceType, it.sourceId) in verifiedScope }) {
            "REJECTED_IDENTITY_HAS_JOURNAL"
        }
        require(journals.all {
            (it.state == DuplicateNormalizationState.PENDING &&
                it.verificationState == DuplicateVerificationState.UNKNOWN && it.verificationDetails == null &&
                it.canonicalWorkId == null && it.planVersion == null && it.planJson == null && it.lastError == null) ||
                it.state == DuplicateNormalizationState.COMPLETED ||
                (it.state == DuplicateNormalizationState.PLANNED &&
                    it.verificationState == DuplicateVerificationState.VERIFIED && it.lastError == null)
        }) { "UNEXPECTED_JOURNAL" }
        val completedJournalIds = journals.filter { it.state == DuplicateNormalizationState.COMPLETED }
            .map { it.id }.toSet()
        require(dao.getAllCleanup().all { it.normalizationId in completedJournalIds }) {
            "UNEXPECTED_CLEANUP_JOURNAL"
        }

        // Plan every verified identity before the first normalization. Rejected identities remain observation-only.
        for (entry in prepared.entries.filter { it.snapshot != null && it.verification is MediaVerificationResult.Verified }) {
            val plan = requireNotNull(entry.plan)
            val existing = dao.getByIdentity(entry.identity.sourceType, entry.identity.sourceId)
            if (existing?.state == DuplicateNormalizationState.PLANNED) {
                check(existing.planVersion == DUPLICATE_PLAN_VERSION && existing.planJson == plan.json &&
                    existing.canonicalWorkId == plan.canonicalWorkId && existing.lastError == null
                ) { "UNEXPECTED_JOURNAL" }
                continue
            }
            val journal = dao.ensureJournal(entry.identity.sourceType, entry.identity.sourceId, now())
            check(dao.finalizeVerification(
                journal.id, requireNotNull(entry.snapshot), DuplicateVerification.verified(), DUPLICATE_PLAN_VERSION,
                plan.json, plan.canonicalWorkId, now()
            )) { "SNAPSHOT_CHANGED" }
        }
        val verifiedEntries = prepared.entries.filter { it.identity in verifiedScope }
        val planned = verifiedEntries.map { requireNotNull(dao.getByIdentity(it.identity.sourceType, it.identity.sourceId)) }
        check(planned.size == verifiedEntries.size && planned.all {
            it.state == DuplicateNormalizationState.COMPLETED ||
                (it.state == DuplicateNormalizationState.PLANNED &&
                    it.verificationState == DuplicateVerificationState.VERIFIED && it.lastError == null)
        }) { "ALL_IDENTITIES_NOT_PLANNED" }

        var normalized = 0
        for (journal in planned) {
            if (!shouldNormalize(journal.state)) continue
            val plan = DuplicateNormalizationPlan.parse(requireNotNull(journal.planJson))
            val error = DuplicatePlanVerifier.verify(plan, mediaReader)
            if (error != null) {
                dao.recordNormalizationError(journal.id, error, now())
                break
            }
            val result = runCatching { dao.applyPlan(journal.id, plan, now()) }.getOrNull()
            val applyError = result?.let(::normalizationErrorFor) ?: if (result == null) DuplicateVerificationDetails.DB_APPLY_FAILED else null
            if (applyError != null) {
                dao.recordNormalizationError(journal.id, applyError, now())
                break
            }
            normalized++
        }
        val verifiedIdentities = verifiedEntries.map { it.identity }.toSet()
        val after = dao.getAllJournals().filter {
            DuplicateIdentityKey(it.sourceType, it.sourceId) in verifiedIdentities
        }
        check(after.size == verifiedEntries.size) { "ALL_IDENTITIES_NOT_PLANNED" }
        val completed = after.count { it.state == DuplicateNormalizationState.COMPLETED }
        if (shouldStartCleanup(verifiedEntries.size, completed)) cleanup.run()
        val cleanupEntries = after.flatMap { dao.getCleanup(it.id) }
        return ScopedDuplicateRunResult(
            prepared.manifest, normalized, completed,
            cleanupEntries.count { it.state == "PENDING" },
            cleanupEntries.count { it.state == "PENDING" && it.lastError != null }
        )
    }

    private suspend fun prepare(identities: List<DuplicateIdentityKey>): PreparedBatch {
        requireStrictIdentityScope(identities)
        val ordered = identities.sortedWith(compareBy({ it.sourceType }, { it.sourceId }))
        val completed = dao.getAllJournals().filter { it.state == DuplicateNormalizationState.COMPLETED }
            .associateBy { DuplicateIdentityKey(it.sourceType, it.sourceId) }
        require(completed.keys.all { it in ordered }) { "UNEXPECTED_JOURNAL" }
        val remaining = ordered.filter { it !in completed }.toSet()
        val actual = dao.getDuplicateIdentities()
        require(actual.map { DuplicateIdentityKey(it.sourceType, it.sourceId) }.distinct().size == actual.size) {
            "DUPLICATE_IDENTITY_ENUMERATION_INVALID"
        }
        val actualKeys = actual.map { DuplicateIdentityKey(it.sourceType, it.sourceId) }.toSet()
        require(actualKeys + completed.keys == ordered.toSet()) { "IDENTITY_SCOPE_NOT_EXACT_MATCH" }
        val observed = actual.map { duplicate ->
            val identity = DuplicateIdentityKey(duplicate.sourceType, duplicate.sourceId)
            val snapshot = dao.snapshot(identity.sourceType, identity.sourceId)
            val measured = snapshot.media.map { media ->
                media to DuplicateMediaVerifier.verifyOne(media.id, media.localUri, mediaReader)
            }
            val result = when {
                snapshot.works.size < 2 -> MediaVerificationResult.Rejected("IDENTITY_NO_LONGER_DUPLICATE")
                snapshot.activeMoves.isNotEmpty() ->
                    MediaVerificationResult.Rejected(DuplicateVerificationDetails.MEDIA_MOVE_IN_PROGRESS)
                measured.any { it.second == null } -> MediaVerificationResult.Rejected(DuplicateVerificationDetails.UNREADABLE)
                measured.groupBy { it.first.mediaIndex }.values.any { group ->
                    group.map { requireNotNull(it.second).byteCount }.distinct().size > 1
                } -> MediaVerificationResult.Rejected(DuplicateVerificationDetails.SIZE_MISMATCH)
                measured.groupBy { it.first.mediaIndex }.values.any { group ->
                    group.map { requireNotNull(it.second).sha256 }.distinct().size > 1
                } -> MediaVerificationResult.Rejected(DuplicateVerificationDetails.HASH_MISMATCH)
                else -> MediaVerificationResult.Verified(measured.map { requireNotNull(it.second) })
            }
            ObservedDuplicate(identity, snapshot, result, measured.map { it.second })
        }
        require(observed.map { it.identity }.toSet() == remaining && observed.size == remaining.size) {
            "IDENTITY_SCOPE_NOT_EXACT_MATCH"
        }
        val entries = ordered.map { identity ->
            completed[identity]?.let { journal ->
                val plan = DuplicateNormalizationPlan.parse(requireNotNull(journal.planJson))
                require(JSONObject(journal.planJson).optInt("scopedManifestVersion") == 1) { "UNEXPECTED_JOURNAL" }
                return@map PreparedEntry(
                    identity, null, DuplicatePlan(plan.canonicalWorkId, journal.planJson), null,
                    plan.mediaSnapshot.map { VerifiedMedia(it.id, it.byteCount, it.sha256) }
                )
            }
            val observation = observed.single { it.identity == identity }
            val snapshot = observation.snapshot
            val plan = when (val verification = observation.verification) {
                is MediaVerificationResult.Verified -> enrichScopedPlan(
                    DuplicateNormalizationPlanner.create(identity.sourceType, identity.sourceId, snapshot, verification.media), snapshot
                )
                is MediaVerificationResult.Rejected -> null
            }
            PreparedEntry(identity, snapshot, plan, observation.verification, observation.media)
        }
        val manifestJson = JSONObject()
            .put("version", 1)
            .put("identities", JSONArray(entries.map { entry ->
                val status = if (entry.snapshot == null || entry.verification is MediaVerificationResult.Verified) "VERIFIED" else "REJECTED"
                JSONObject().put("sourceType", entry.identity.sourceType).put("sourceId", entry.identity.sourceId)
                    .put("status", status)
                    .put("details", (entry.verification as? MediaVerificationResult.Rejected)?.reason ?: JSONObject.NULL)
                    .put("media", JSONArray(if (entry.snapshot == null) {
                        entry.media.filterNotNull().sortedBy { it.mediaId }.map { measured ->
                            val item = requireNotNull(entry.plan).let { DuplicateNormalizationPlan.parse(it.json) }
                                .mediaSnapshot.single { it.id == measured.mediaId }
                            JSONObject().put("id", measured.mediaId).put("mediaIndex", item.mediaIndex)
                                .put("byteCount", measured.byteCount).put("sha256", measured.sha256)
                        }
                    } else entry.snapshot.media.sortedBy { it.id }.map { media ->
                        val measured = entry.media.filterNotNull().singleOrNull { it.mediaId == media.id }
                        JSONObject().put("id", media.id).put("mediaIndex", media.mediaIndex)
                            .put("byteCount", measured?.byteCount ?: JSONObject.NULL)
                            .put("sha256", measured?.sha256 ?: JSONObject.NULL)
                    }))
                    .put("plan", entry.plan?.let { JSONObject(it.json) } ?: JSONObject.NULL)
            }))
            .toString()
        val fingerprint = sha256Fingerprint(manifestJson)
        return PreparedBatch(entries, ScopedDuplicateManifest(manifestJson, fingerprint))
    }

    private data class PreparedEntry(
        val identity: DuplicateIdentityKey,
        val snapshot: DuplicateIdentitySnapshot?,
        val plan: DuplicatePlan?,
        val verification: MediaVerificationResult?,
        val media: List<VerifiedMedia?>
    )
    private data class PreparedBatch(val entries: List<PreparedEntry>, val manifest: ScopedDuplicateManifest)
    private data class ObservedDuplicate(
        val identity: DuplicateIdentityKey,
        val snapshot: DuplicateIdentitySnapshot,
        val verification: MediaVerificationResult,
        val media: List<VerifiedMedia?>
    )

    private fun enrichScopedPlan(base: DuplicatePlan, snapshot: DuplicateIdentitySnapshot): DuplicatePlan {
        fun JSONObject.nullable(name: String, value: Any?) = put(name, value ?: JSONObject.NULL)
        val works = snapshot.works.sortedBy { it.id }.map { work ->
            JSONObject().put("id", work.id).put("sourceType", work.sourceType).put("sourceId", work.sourceId)
                .put("canonicalUrl", work.canonicalUrl).put("capturedAt", work.capturedAt)
                .nullable("publishedAt", work.publishedAt).nullable("discoveryMode", work.discoveryMode)
                .nullable("discoveryQuery", work.discoveryQuery).put("authorId", work.authorId)
                .put("authorName", work.authorName).nullable("authorHandle", work.authorHandle)
                .nullable("title", work.title).put("caption", work.caption).nullable("sessionId", work.sessionId)
        }
        val media = snapshot.media.sortedBy { it.id }.map { item ->
            JSONObject().put("id", item.id).put("workId", item.workId).put("mediaIndex", item.mediaIndex)
                .put("localUri", item.localUri).put("sourceUrl", item.sourceUrl).put("mimeType", item.mimeType)
                .put("fileName", item.fileName).put("isHighlighted", item.isHighlighted)
        }
        val json = JSONObject(base.json).put("scopedManifestVersion", 1)
            .put("scopeSnapshot", JSONObject().put("works", JSONArray(works)).put("media", JSONArray(media)))
            .toString()
        return DuplicatePlan(base.canonicalWorkId, json)
    }
}
