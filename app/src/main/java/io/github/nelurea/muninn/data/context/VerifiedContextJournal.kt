package io.github.nelurea.muninn.data.context

import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** A SAF document with the display name reported by its provider, not a guessed filename. */
data class ContextDocument(val uri: String, val displayName: String)

/** File-like operations; implementations must not overwrite existing documents in create(). */
interface ContextDocumentStore {
    fun list(): List<ContextDocument>
    fun create(displayName: String, mimeType: String): ContextDocument
    fun write(document: ContextDocument, bytes: ByteArray)
    fun read(document: ContextDocument): ByteArray
}

data class ContextCommitReceipt(
    val generation: String,
    val committedAtMs: Long,
    val sha256: String,
    val length: Int
)

data class RecoveredContextSnapshot(
    val receipt: ContextCommitReceipt,
    val bytes: ByteArray
)

/**
 * Append-only, two-document commit protocol. A payload is created and verified first;
 * only then is a unique commit marker written and verified. Readers ignore uncommitted
 * payloads and invalid markers and fall back to the newest verified older generation.
 *
 * This is not a filesystem fsync/rename guarantee: a SAF provider controls its own
 * storage durability. It prevents returning success for observable write/read failures
 * and avoids modifying an already committed generation.
 *
 * The payload is opaque here. Schema validation and Room reconciliation are the
 * responsibility of the context snapshot layer, not this storage primitive.
 */
class VerifiedContextJournal(
    private val documents: ContextDocumentStore,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val generationId: () -> String = { UUID.randomUUID().toString().replace("-", "") }
) {
    @Synchronized
    @Throws(IOException::class)
    fun save(snapshot: ByteArray): ContextCommitReceipt {
        if (snapshot.size > MAX_BYTES) throw IOException("Context snapshot exceeds the size limit")
        val id = generationId().lowercase(Locale.ROOT)
        if (!ID.matches(id)) throw IOException("Invalid generation identity")
        val time = clockMs()
        if (time < 0) throw IOException("Invalid commit time")
        val checksum = sha256(snapshot)
        val payloadName = "$DATA_PREFIX$id.bin"
        val payloadDoc = documents.create(payloadName, "application/octet-stream")
        if (!isSafeName(payloadDoc.displayName) || !payloadDoc.displayName.startsWith("$DATA_PREFIX$id")) {
            throw IOException("SAF provider supplied an unsafe payload name")
        }
        documents.write(payloadDoc, snapshot)
        if (sha256(documents.read(payloadDoc)) != checksum) {
            throw IOException("Context payload read-back verification failed")
        }
        val commitBytes = encodeCommit(id, time, payloadDoc.displayName, snapshot.size, checksum)
        val markerDoc = documents.create("$MARKER_PREFIX$id.commit", "application/octet-stream")
        if (!isSafeName(markerDoc.displayName) || !markerDoc.displayName.startsWith("$MARKER_PREFIX$id")) {
            throw IOException("SAF provider supplied an unsafe marker name")
        }
        documents.write(markerDoc, commitBytes)
        if (!documents.read(markerDoc).contentEquals(commitBytes)) {
            throw IOException("Context marker read-back verification failed")
        }
        // Re-read the payload: a provider may have made earlier bytes unavailable.
        if (sha256(documents.read(payloadDoc)) != checksum) {
            throw IOException("Context payload disappeared before commit confirmation")
        }
        return ContextCommitReceipt(id, time, checksum, snapshot.size)
    }

    /** Null means there is no committed generation. A malformed-only journal is an error. */
    @Synchronized
    @Throws(IOException::class)
    fun readLatest(): RecoveredContextSnapshot? {
        val files = documents.list()
        val markers = files.filter { it.displayName.startsWith(MARKER_PREFIX) }
        if (markers.isEmpty()) return null
        val recovered = ArrayList<RecoveredContextSnapshot>()
        for (marker in markers) {
            try {
                val info = parseCommit(documents.read(marker))
                if (!marker.displayName.startsWith("$MARKER_PREFIX${info.generation}")) continue
                val candidates = files.filter { it.displayName == info.payloadName }
                if (candidates.size != 1) continue
                val payload = documents.read(candidates.single())
                if (payload.size != info.length || sha256(payload) != info.sha256) continue
                recovered += RecoveredContextSnapshot(
                    ContextCommitReceipt(info.generation, info.time, info.sha256, info.length),
                    payload
                )
            } catch (_: IOException) {
                // A broken or incomplete new generation must not hide a valid older one.
            } catch (_: IllegalArgumentException) {
                // A malformed marker does not replace the last committed snapshot.
            }
        }
        if (recovered.isEmpty()) throw IOException("No valid committed Context generation")
        return recovered.maxWithOrNull(
            compareBy<RecoveredContextSnapshot> { it.receipt.committedAtMs }
                .thenBy { it.receipt.generation }
        )
    }

    private data class Marker(
        val generation: String,
        val time: Long,
        val payloadName: String,
        val length: Int,
        val sha256: String
    )

    private fun encodeCommit(id: String, time: Long, name: String, length: Int, hash: String): ByteArray =
        "$MAGIC\n$id\n$time\n$name\n$length\n$hash\n".toByteArray(Charsets.UTF_8)

    private fun parseCommit(bytes: ByteArray): Marker {
        if (bytes.size > 2048) throw IOException("Oversized commit marker")
        val lines = bytes.toString(Charsets.UTF_8).split('\n')
        if (lines.size != 7 || lines[0] != MAGIC || lines[6].isNotEmpty()) {
            throw IOException("Invalid commit marker format")
        }
        val id = lines[1]
        val time = lines[2].toLongOrNull() ?: throw IOException("Invalid commit timestamp")
        val name = lines[3]
        val length = lines[4].toIntOrNull() ?: throw IOException("Invalid snapshot size")
        val checksum = lines[5]
        if (!ID.matches(id) || time < 0 || length !in 0..MAX_BYTES ||
            !isSafeName(name) || !name.startsWith("$DATA_PREFIX$id") ||
            !HASH.matches(checksum)) {
            throw IOException("Invalid commit marker values")
        }
        return Marker(id, time, name, length, checksum)
    }

    private fun isSafeName(value: String) = value.length in 1..160 && NAME.matches(value)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val MAX_BYTES = 64 * 1024 * 1024
        const val MAGIC = "MUNINN_CONTEXT_COMMIT_V1"
        const val DATA_PREFIX = "muninn-context-data-v1-"
        const val MARKER_PREFIX = "muninn-context-commit-v1-"
        val ID = Regex("[0-9a-f]{32}")
        val HASH = Regex("[0-9a-f]{64}")
        val NAME = Regex("[A-Za-z0-9._-]+")
    }
}
