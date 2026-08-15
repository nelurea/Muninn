package io.github.nelurea.muninn.discovery.x

import io.github.nelurea.muninn.capture.web.x.XCaptureParser
import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import org.json.JSONObject

data class XDiscoveryBatch(
    val mode: DiscoveryMode,
    val query: String?,
    val items: List<XCapturePayload>
)

sealed interface XDiscoveryBatchParseResult {

    data class Success(
        val batch: XDiscoveryBatch
    ) : XDiscoveryBatchParseResult

    data class Failure(
        val error: String
    ) : XDiscoveryBatchParseResult
}

object XDiscoveryBatchParser {

    fun parse(
        rawMessage: String
    ): XDiscoveryBatchParseResult {

        return try {
            val root =
                JSONObject(
                    rawMessage
                )

            if (
                root.optString(
                    "type"
                ) != "X_DISCOVERY_BATCH"
            ) {
                return XDiscoveryBatchParseResult.Failure(
                    "Unsupported X Discovery bridge message type"
                )
            }

            val mode =
                DiscoveryMode.valueOf(
                    root.getString(
                        "mode"
                    )
                )

            val query =
                if (
                    root.has(
                        "query"
                    ) &&
                    !root.isNull(
                        "query"
                    )
                ) {
                    root.getString(
                        "query"
                    )
                        .trim()
                        .takeIf {
                            it.isNotBlank()
                        }
                } else {
                    null
                }

            val rawItems =
                root.getJSONArray(
                    "items"
                )

            val items =
                buildList {
                    for (
                    index in
                    0 until rawItems.length()
                    ) {
                        add(
                            XCaptureParser
                                .parseCapturePackage(
                                    rawItems
                                        .getJSONObject(
                                            index
                                        )
                                )
                        )
                    }
                }

            XDiscoveryBatchParseResult.Success(
                XDiscoveryBatch(
                    mode =
                        mode,
                    query =
                        query,
                    items =
                        items
                )
            )
        } catch (
            exception: Exception
        ) {
            XDiscoveryBatchParseResult.Failure(
                exception.message
                    ?: "Could not parse X Discovery batch"
            )
        }
    }
}