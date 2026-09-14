package io.github.nelurea.muninn.settings

import io.github.nelurea.muninn.content.ContentRestriction
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentVisibilityPolicyTest {
    @Test
    fun generalAndUnknownAreAlwaysVisible() {
        val policy =
            SensitiveContentVisibilityPolicy(
                threshold = ContentRatingLevel.R18,
                visibilityMode = SensitiveVisibilityMode.HIDDEN
            )

        assertEquals(
            ContentVisibility.VISIBLE,
            ContentRestriction.GENERAL.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.VISIBLE,
            ContentRestriction.UNKNOWN.visibilityFor(policy)
        )
    }

    @Test
    fun thresholdAndModeControlRestrictedContent() {
        val policy =
            SensitiveContentVisibilityPolicy(
                threshold = ContentRatingLevel.SENSITIVE,
                visibilityMode = SensitiveVisibilityMode.BLURRED
            )

        assertEquals(
            ContentVisibility.VISIBLE,
            ContentRestriction.R18.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.BLURRED,
            ContentRestriction.SENSITIVE.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.BLURRED,
            ContentRestriction.R18G.visibilityFor(policy)
        )
    }

    @Test
    fun hiddenModeHidesRestrictedContent() {
        val policy =
            SensitiveContentVisibilityPolicy(
                threshold = ContentRatingLevel.R18,
                visibilityMode = SensitiveVisibilityMode.HIDDEN
            )

        assertEquals(
            ContentVisibility.HIDDEN,
            ContentRestriction.R18.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.HIDDEN,
            ContentRestriction.SENSITIVE.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.HIDDEN,
            ContentRestriction.R18G.visibilityFor(policy)
        )
    }

    @Test
    fun visibleModeShowsRestrictedContent() {
        val policy =
            SensitiveContentVisibilityPolicy(
                threshold = ContentRatingLevel.R18,
                visibilityMode = SensitiveVisibilityMode.VISIBLE
            )

        assertEquals(
            ContentVisibility.VISIBLE,
            ContentRestriction.R18.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.VISIBLE,
            ContentRestriction.SENSITIVE.visibilityFor(policy)
        )
        assertEquals(
            ContentVisibility.VISIBLE,
            ContentRestriction.R18G.visibilityFor(policy)
        )
    }
}
