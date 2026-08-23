package io.github.nelurea.muninn.duplicate

import io.github.nelurea.muninn.data.db.DuplicateNormalizationApplyResult
import io.github.nelurea.muninn.data.db.DuplicateVerificationDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateNormalizationCoordinatorTest {
    @Test
    fun normalRunSelectsOnlyPlannedRowsWithoutLastError() {
        assertFalse(plannedErrorFilter(retryFailed = false))
    }

    @Test
    fun retrySelectsOnlyPlannedRowsWithLastError() {
        assertTrue(plannedErrorFilter(retryFailed = true))
    }

    @Test
    fun applyResultsMapToStableNormalizationErrorCodes() {
        assertNull(normalizationErrorFor(DuplicateNormalizationApplyResult.APPLIED))
        assertEquals(
            DuplicateVerificationDetails.SNAPSHOT_CHANGED,
            normalizationErrorFor(DuplicateNormalizationApplyResult.SNAPSHOT_CHANGED)
        )
        assertEquals(
            DuplicateVerificationDetails.MEDIA_MOVE_IN_PROGRESS,
            normalizationErrorFor(DuplicateNormalizationApplyResult.MEDIA_MOVE_IN_PROGRESS)
        )
    }
}
