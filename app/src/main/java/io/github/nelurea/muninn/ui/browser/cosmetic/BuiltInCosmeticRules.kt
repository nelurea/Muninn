package io.github.nelurea.muninn.ui.browser.cosmetic

object BuiltInCosmeticRules {

    val rules =
        listOf(
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    "[data-ga4-label=\"comment\"]"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    ".work-details-comments-container"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    ".work-stats > *:has(pixiv-icon[name=\"Inline/Smile\"])"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    ".work-stats > *:has(pixiv-icon[name=\"Inline/Like\"])"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    ".work-stats > *:has(pixiv-icon[name=\"Inline/View\"])"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    ".work-stats > pixiv-icon[name=\"Inline/Smile\"]"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    ".work-stats > pixiv-icon[name=\"Inline/Smile\"] + span"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    "[data-intersection-key=\"recommend-infinite-illust-grid-list-related\"]"
            ),
            CosmeticRule(
                host =
                    "www.pixiv.net",
                selector =
                    "[data-intersection-key=\"recommend-infinite-illust-grid-list-discovery\"]"
            )
        )
}