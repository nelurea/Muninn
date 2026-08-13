package io.github.nelurea.muninn.capture.discovery

import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivDiscoverySaveUseCaseTest {

    @Test
    fun saveAllPreservesAllMediaAndHighlightsSelectedPages() {
        val preview =
            syntheticPreview()

        val result =
            buildDiscoveryArtworkSavePlan(
                preview =
                    preview,
                selectedMediaIndices =
                    setOf(
                        1,
                        3
                    ),
                mode =
                    DiscoveryArtworkSaveMode.ALL
            )

        assertTrue(
            result is
                    DiscoveryArtworkSavePlan.Ready
        )

        val items =
            (
                    result as
                            DiscoveryArtworkSavePlan.Ready
                    ).items

        assertEquals(
            listOf(
                0,
                1,
                2,
                3
            ),
            items.map {
                it.mediaIndex
            }
        )

        assertEquals(
            listOf(
                false,
                true,
                false,
                true
            ),
            items.map {
                it.isHighlighted
            }
        )
    }

    @Test
    fun saveSelectedPersistsOnlySelectionAndPreservesOriginalIndices() {
        val preview =
            syntheticPreview()

        val result =
            buildDiscoveryArtworkSavePlan(
                preview =
                    preview,
                selectedMediaIndices =
                    setOf(
                        1,
                        3
                    ),
                mode =
                    DiscoveryArtworkSaveMode.SELECTED
            )

        assertTrue(
            result is
                    DiscoveryArtworkSavePlan.Ready
        )

        val items =
            (
                    result as
                            DiscoveryArtworkSavePlan.Ready
                    ).items

        assertEquals(
            listOf(
                1,
                3
            ),
            items.map {
                it.mediaIndex
            }
        )

        assertEquals(
            listOf(
                true,
                true
            ),
            items.map {
                it.isHighlighted
            }
        )

        assertEquals(
            listOf(
                "https://synthetic.invalid/media/page-1.jpg",
                "https://synthetic.invalid/media/page-3.jpg"
            ),
            items.map {
                it.originalUrl
            }
        )
    }

    @Test
    fun saveSelectedFailsWhenNothingIsSelected() {
        val preview =
            syntheticPreview()

        val result =
            buildDiscoveryArtworkSavePlan(
                preview =
                    preview,
                selectedMediaIndices =
                    emptySet(),
                mode =
                    DiscoveryArtworkSaveMode.SELECTED
            )

        assertTrue(
            result is
                    DiscoveryArtworkSavePlan.Failure
        )

        val failure =
            result as
                    DiscoveryArtworkSavePlan.Failure

        assertEquals(
            "No artwork pages were selected.",
            failure.error
        )
    }

    private fun syntheticPreview(): ArtworkPreview {
        return ArtworkPreview(
            source =
                DiscoverySourceId.PIXIV,

            sourceItemId =
                "synthetic-work-001",

            canonicalUrl =
                "https://synthetic.invalid/artworks/synthetic-work-001",

            publishedAt =
                "2026-08-01T00:00:00.000Z",

            title =
                "Synthetic multi-page artwork",

            caption =
                "Synthetic caption",

            creator =
                DiscoveryCreator(
                    sourceCreatorId =
                        "synthetic-user-001",

                    name =
                        "Synthetic Creator"
                ),

            creatorAvatarUrl =
                "https://synthetic.invalid/avatar.jpg",

            tags =
                listOf(
                    "synthetic-tag-a",
                    "synthetic-tag-b"
                ),

            media =
                List(
                    4
                ) {
                        index ->

                    ArtworkPreviewMedia(
                        mediaIndex =
                            index,

                        previewUrl =
                            "https://synthetic.invalid/preview/page-$index.jpg",

                        originalUrl =
                            "https://synthetic.invalid/media/page-$index.jpg"
                    )
                }
        )
    }
}