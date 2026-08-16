package io.github.nelurea.muninn.capture.discovery

import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.ArtworkPreviewMedia
import io.github.nelurea.muninn.discovery.model.DiscoveryCreator
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverySaveCoordinatorTest {

    @Test
    fun sameArtworkCannotRunSelectedAndAllSavesConcurrently() {
        val started =
            CountDownLatch(
                1
            )

        val release =
            CountDownLatch(
                1
            )

        val fakeUseCase =
            object :
                DiscoveryArtworkSaveUseCase {

                override val sourceId =
                    DiscoverySourceId.PIXIV

                override suspend fun save(
                    preview: ArtworkPreview,
                    selectedMediaIndices: Set<Int>,
                    mode: DiscoveryArtworkSaveMode,
                    discoveryMode: String?,
                    discoveryQuery: String?
                ): SaveCaptureResult {

                    started.countDown()

                    release.await(
                        2,
                        TimeUnit.SECONDS
                    )

                    return SaveCaptureResult.Success(
                        workId =
                            1L,
                        mediaCount =
                            1
                    )
                }
            }

        val coordinator =
            DiscoverySaveCoordinator(
                mapOf(
                    DiscoverySourceId.PIXIV to
                        fakeUseCase
                )
            )

        try {
            val preview =
                syntheticPreview()

            val first =
                coordinator.enqueue(
                    preview =
                        preview,
                    selectedMediaIndices =
                        setOf(
                            0
                        ),
                    mode =
                        DiscoveryArtworkSaveMode.SELECTED,
                    discoveryMode =
                        null,
                    discoveryQuery =
                        null
                )

            assertTrue(
                first is
                    DiscoverySaveEnqueueResult.Accepted
            )

            assertTrue(
                started.await(
                    1,
                    TimeUnit.SECONDS
                )
            )

            val second =
                coordinator.enqueue(
                    preview =
                        preview,
                    selectedMediaIndices =
                        emptySet(),
                    mode =
                        DiscoveryArtworkSaveMode.ALL,
                    discoveryMode =
                        null,
                    discoveryQuery =
                        null
                )

            assertTrue(
                second is
                    DiscoverySaveEnqueueResult.AlreadyRunning
            )
        } finally {
            release.countDown()
            coordinator.close()
        }
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
                "Synthetic artwork",

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
                null,

            tags =
                emptyList(),

            media =
                listOf(
                    ArtworkPreviewMedia(
                        mediaIndex =
                            0,
                        previewUrl =
                            "https://synthetic.invalid/preview.jpg",
                        originalUrl =
                            "https://synthetic.invalid/original.jpg"
                    )
                )
        )
    }
}
