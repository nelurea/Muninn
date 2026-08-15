package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.discovery.DiscoveryPage
import io.github.nelurea.muninn.discovery.DiscoverySource
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

class XDiscoverySource(
    private val observationStore:
    XDiscoveryObservationStore =
        XDiscoveryObservationStore
) : DiscoverySource {

    override val sourceId =
        DiscoverySourceId.X

    override suspend fun load(
        mode: DiscoveryMode,
        page: Int,
        query: String?
    ): DiscoveryPage {

        require(
            page >= 1
        ) {
            "page must be >= 1"
        }

        val normalizedQuery =
            when (
                mode
            ) {
                DiscoveryMode.SEARCH -> {
                    query
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: throw XDiscoveryException(
                            "Search query must not be blank."
                        )
                }

                else ->
                    null
            }

        val observedItems =
            observationStore.snapshot(
                mode =
                    mode,
                query =
                    normalizedQuery
            )

        val startIndex =
            (
                    page -
                            1
                    ) *
                    PAGE_SIZE

        if (
            startIndex >=
            observedItems.size
        ) {
            return DiscoveryPage(
                items =
                    emptyList(),
                page =
                    page,
                hasNextPage =
                    false
            )
        }

        val endIndex =
            minOf(
                startIndex +
                        PAGE_SIZE,
                observedItems.size
            )

        val pageItems =
            observedItems.subList(
                startIndex,
                endIndex
            )

        return DiscoveryPage(
            items =
                XDiscoveryMapper
                    .mapAll(
                        pageItems
                    ),
            page =
                page,
            hasNextPage =
                endIndex <
                        observedItems.size
        )
    }

    private companion object {

        const val PAGE_SIZE =
            40
    }
}

class XDiscoveryException(
    message: String,
    cause: Throwable? = null
) : Exception(
    message,
    cause
)