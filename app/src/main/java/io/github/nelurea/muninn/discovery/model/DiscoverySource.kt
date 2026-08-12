package io.github.nelurea.muninn.discovery

import io.github.nelurea.muninn.discovery.model.DiscoveryItem
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId

interface DiscoverySource {

    val sourceId: DiscoverySourceId

    suspend fun loadLatest(
        page: Int
    ): DiscoveryPage
}

data class DiscoveryPage(
    val items: List<DiscoveryItem>,
    val page: Int,
    val hasNextPage: Boolean
)