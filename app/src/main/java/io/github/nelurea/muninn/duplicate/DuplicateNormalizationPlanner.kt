package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.CapturedMediaEntity
import io.github.nelurea.muninn.data.db.DuplicateIdentitySnapshot
import org.json.JSONArray
import org.json.JSONObject

internal const val DUPLICATE_PLAN_VERSION = 2

data class VerifiedMedia(val mediaId: Long, val byteCount: Long, val sha256: String)

data class DuplicatePlan(val canonicalWorkId: Long, val json: String)

object DuplicateNormalizationPlanner {
    fun create(
        sourceType: String,
        sourceId: String,
        snapshot: DuplicateIdentitySnapshot,
        verified: List<VerifiedMedia>
    ): DuplicatePlan {
        require(snapshot.works.size > 1)
        val canonicalWork = snapshot.works.minWith(compareBy({ it.capturedAt }, { it.id }))
        val rank = snapshot.works.sortedWith(compareBy({ it.capturedAt }, { it.id }))
            .mapIndexed { index, work -> work.id to index }.toMap()
        val mediaById = snapshot.media.associateBy { it.id }
        val canonicalMedia = snapshot.media.groupBy { it.mediaIndex }.toSortedMap().map { (mediaIndex, candidates) ->
            val selected = candidates.minWith(compareBy<CapturedMediaEntity>({ rank.getValue(it.workId) }, { it.id }))
            JSONObject().put("mediaIndex", mediaIndex).put("canonicalMediaId", selected.id).put(
                "duplicateMediaIds",
                JSONArray(candidates.map { it.id }.filter { it != selected.id }.sorted())
            )
        }
        require(verified.map { it.mediaId }.toSet() == mediaById.keys)
        val verifiedById = verified.associateBy { it.mediaId }
        val mediaSnapshot = snapshot.media
            .sortedWith(compareBy({ it.mediaIndex }, { it.workId }, { it.id }))
            .map { item ->
                val verification = verifiedById.getValue(item.id)
                JSONObject()
                    .put("id", item.id)
                    .put("workId", item.workId)
                    .put("mediaIndex", item.mediaIndex)
                    .put("localUri", item.localUri)
                    .put("byteCount", verification.byteCount)
                    .put("sha256", verification.sha256)
            }
        val json = JSONObject()
            .put("version", DUPLICATE_PLAN_VERSION)
            .put("sourceType", sourceType)
            .put("sourceId", sourceId)
            .put("canonicalWorkId", canonicalWork.id)
            .put("duplicateWorkIds", JSONArray(snapshot.works.map { it.id }.filter { it != canonicalWork.id }.sorted()))
            .put("media", JSONArray(canonicalMedia))
            .put("mediaSnapshot", JSONArray(mediaSnapshot))
            .toString()
        return DuplicatePlan(canonicalWork.id, json)
    }
}
