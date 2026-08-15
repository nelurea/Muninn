package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import io.github.nelurea.muninn.discovery.model.DiscoveryMode

data class XDiscoveryMergeResult(
    val receivedCount: Int,
    val addedCount: Int,
    val totalCount: Int
)

object XDiscoveryObservationStore {

    private data class FeedKey(
        val mode: DiscoveryMode,
        val query: String?
    )

    private val feeds =
        mutableMapOf<
                FeedKey,
                LinkedHashMap<String, XCapturePayload>
                >()

    private val itemsBySourceId =
        LinkedHashMap<
                String,
                XCapturePayload
                >()

    fun merge(
        batch: XDiscoveryBatch
    ): XDiscoveryMergeResult =
        synchronized(
            this
        ) {
            val key =
                FeedKey(
                    mode =
                        batch.mode,
                    query =
                        normalizeQuery(
                            batch.mode,
                            batch.query
                        )
                )

            val feed =
                feeds.getOrPut(
                    key
                ) {
                    LinkedHashMap()
                }

            val previousSize =
                feed.size

            batch.items.forEach {
                    item ->

                feed[
                    item.sourceId
                ] =
                    item

                itemsBySourceId[
                    item.sourceId
                ] =
                    item
            }

            XDiscoveryMergeResult(
                receivedCount =
                    batch.items.size,
                addedCount =
                    feed.size -
                            previousSize,
                totalCount =
                    feed.size
            )
        }

    fun snapshot(
        mode: DiscoveryMode,
        query: String? = null
    ): List<XCapturePayload> =
        synchronized(
            this
        ) {
            val key =
                FeedKey(
                    mode =
                        mode,
                    query =
                        normalizeQuery(
                            mode,
                            query
                        )
                )

            feeds[
                key
            ]
                ?.values
                ?.toList()
                .orEmpty()
        }

    fun findBySourceId(
        sourceId: String
    ): XCapturePayload? =
        synchronized(
            this
        ) {
            itemsBySourceId[
                sourceId
            ]
        }

    fun clear(
        mode: DiscoveryMode,
        query: String? = null
    ) {
        synchronized(
            this
        ) {
            feeds.remove(
                FeedKey(
                    mode =
                        mode,
                    query =
                        normalizeQuery(
                            mode,
                            query
                        )
                )
            )

            rebuildGlobalIndex()
        }
    }

    fun clearAll() {
        synchronized(
            this
        ) {
            feeds.clear()
            itemsBySourceId.clear()
        }
    }

    private fun rebuildGlobalIndex() {
        itemsBySourceId.clear()

        feeds
            .values
            .forEach {
                    feed ->

                feed
                    .values
                    .forEach {
                            item ->

                        itemsBySourceId[
                            item.sourceId
                        ] =
                            item
                    }
            }
    }

    private fun normalizeQuery(
        mode: DiscoveryMode,
        query: String?
    ): String? {
        if (
            mode !=
            DiscoveryMode.SEARCH
        ) {
            return null
        }

        return query
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }
}