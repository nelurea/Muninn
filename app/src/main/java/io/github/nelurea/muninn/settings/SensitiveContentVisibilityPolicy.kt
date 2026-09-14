package io.github.nelurea.muninn.settings

enum class ContentRatingLevel(
    val level: Int
) {
    R18(0),
    SENSITIVE(1),
    R18G(2)
}

enum class SensitiveVisibilityMode {
    HIDDEN,
    BLURRED,
    VISIBLE
}

data class SensitiveContentVisibilityPolicy(
    val threshold: ContentRatingLevel =
        ContentRatingLevel.SENSITIVE,
    val visibilityMode:
    SensitiveVisibilityMode =
        SensitiveVisibilityMode.BLURRED
)