package io.github.nelurea.muninn.settings

import io.github.nelurea.muninn.content.ContentRestriction

enum class ContentVisibility {
    VISIBLE,
    BLURRED,
    HIDDEN
}

fun ContentRestriction.visibilityFor(
    policy: SensitiveContentVisibilityPolicy
): ContentVisibility {
    val restricted =
        when (this) {
            ContentRestriction.GENERAL,
            ContentRestriction.UNKNOWN -> false

            ContentRestriction.R18 ->
                ContentRatingLevel.R18.level >=
                    policy.threshold.level

            ContentRestriction.SENSITIVE ->
                ContentRatingLevel.SENSITIVE.level >=
                    policy.threshold.level

            ContentRestriction.R18G ->
                ContentRatingLevel.R18G.level >=
                    policy.threshold.level
        }

    if (!restricted) {
        return ContentVisibility.VISIBLE
    }

    return when (policy.visibilityMode) {
        SensitiveVisibilityMode.HIDDEN ->
            ContentVisibility.HIDDEN

        SensitiveVisibilityMode.BLURRED ->
            ContentVisibility.BLURRED

        SensitiveVisibilityMode.VISIBLE ->
            ContentVisibility.VISIBLE
    }
}
