package io.github.nelurea.muninn.ui.capture

/**
 * Loading scopes are deliberately distinct: work-level data is not invalidated by
 * changing pages, while page-level data is invalidated by either kind of switch.
 */
internal enum class ContextLoadScope {
    WORK,
    MEDIA
}

internal enum class ContextLoadSection(val scope: ContextLoadScope) {
    PURPOSE(ContextLoadScope.WORK),
    ATTRACTION_VOCABULARY(ContextLoadScope.WORK),
    WORK_ATTRACTIONS(ContextLoadScope.WORK),
    PAGE_ATTRACTIONS(ContextLoadScope.MEDIA),
    RESPONSE(ContextLoadScope.WORK),
    FOCUS(ContextLoadScope.MEDIA)
}

/**
 * A target token can be captured before suspending for a repository mutation.
 * It identifies the work/page that the user actually acted on.
 */
internal data class ContextTargetToken(
    val workId: Long,
    val mediaId: Long?,
    val scope: ContextLoadScope,
    internal val generation: Long
)

/**
 * A load token also carries a per-section serial number so that an earlier,
 * slower query cannot replace a more recent query for the same target.
 */
internal data class ContextLoadToken(
    val target: ContextTargetToken,
    val section: ContextLoadSection,
    internal val serial: Long
)

/**
 * Pure Kotlin scope coordinator. Keep it owned by one Context Editor instance.
 * It deliberately does not store database values or touch Android/Compose APIs.
 */
internal class ContextLoadGate {
    private var hasTarget = false
    private var workId: Long = 0
    private var mediaId: Long? = null
    private var workGeneration = 0L
    private var mediaGeneration = 0L
    private val sectionSerials = mutableMapOf<ContextLoadSection, Long>()

    fun bindTarget(newWorkId: Long, newMediaId: Long?) {
        if (!hasTarget || workId != newWorkId) {
            hasTarget = true
            workId = newWorkId
            mediaId = newMediaId
            workGeneration++
            mediaGeneration++
        } else if (mediaId != newMediaId) {
            mediaId = newMediaId
            mediaGeneration++
        }
    }

    fun captureWork(workId: Long): ContextTargetToken? {
        if (!hasTarget || this.workId != workId) return null
        return ContextTargetToken(workId, null, ContextLoadScope.WORK, workGeneration)
    }

    fun captureMedia(workId: Long, mediaId: Long?): ContextTargetToken? {
        if (!hasTarget || this.workId != workId || this.mediaId != mediaId) return null
        return ContextTargetToken(workId, mediaId, ContextLoadScope.MEDIA, mediaGeneration)
    }

    fun beginLoad(
        section: ContextLoadSection,
        workId: Long,
        mediaId: Long? = null
    ): ContextLoadToken? {
        val target = when (section.scope) {
            ContextLoadScope.WORK -> captureWork(workId)
            ContextLoadScope.MEDIA -> captureMedia(workId, mediaId)
        } ?: return null

        val nextSerial = (sectionSerials[section] ?: 0L) + 1
        sectionSerials[section] = nextSerial
        return ContextLoadToken(target, section, nextSerial)
    }

    fun isTargetCurrent(token: ContextTargetToken): Boolean = when (token.scope) {
        ContextLoadScope.WORK ->
            hasTarget && workId == token.workId && workGeneration == token.generation
        ContextLoadScope.MEDIA ->
            hasTarget && workId == token.workId && mediaId == token.mediaId &&
                mediaGeneration == token.generation
    }

    fun mayApply(token: ContextLoadToken): Boolean =
        token.target.scope == token.section.scope &&
            isTargetCurrent(token.target) &&
            sectionSerials[token.section] == token.serial
}
