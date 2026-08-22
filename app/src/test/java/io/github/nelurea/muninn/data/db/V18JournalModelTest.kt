package io.github.nelurea.muninn.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class V18JournalModelTest {
    @Test
    fun legacySaveEventCanRepresentUnknownUnrecoverableAttributes() {
        val event = SaveEventEntity(
            sourceType = null,
            sourceId = null,
            canonicalUrl = null,
            savedAt = null
        )
        val media = SaveEventMediaEntity(
            saveEventId = 1,
            mediaIndex = null,
            localUri = null,
            sourceUrl = null,
            mimeType = null,
            fileName = null
        )

        assertEquals(SaveEventOrigin.UNKNOWN, event.origin)
        assertEquals(SaveKind.LEGACY, event.saveKind)
        assertNull(event.legacyWorkId)
        assertNull(event.canonicalWorkId)
        assertNull(event.discoveryMode)
        assertNull(event.discoveryQuery)
        assertNull(media.capturedMediaId)
        assertNull(media.mediaIndex)
        assertNull(media.wasRequested)
        assertNull(media.wasHighlighted)
        assertNull(media.wasNewlyStored)
        assertFalse(media.isLegacyBackfill)
    }

    @Test
    fun normalizationJournalStartsWithoutVerificationOrPlan() {
        val journal = DuplicateNormalizationJournalEntity(
            sourceType = "PIXIV",
            sourceId = "123",
            createdAt = 10,
            updatedAt = 10
        )

        assertEquals(DuplicateNormalizationState.PENDING, journal.state)
        assertEquals(DuplicateVerificationState.UNKNOWN, journal.verificationState)
        assertNull(journal.canonicalWorkId)
        assertNull(journal.planVersion)
        assertNull(journal.planJson)
    }
}
