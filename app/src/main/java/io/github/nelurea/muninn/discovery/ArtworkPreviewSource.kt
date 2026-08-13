package io.github.nelurea.muninn.discovery

import io.github.nelurea.muninn.discovery.model.ArtworkPreview
import io.github.nelurea.muninn.discovery.model.DiscoveryItem

interface ArtworkPreviewSource {

    suspend fun load(
        item: DiscoveryItem
    ): ArtworkPreview
}