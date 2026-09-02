package io.github.nelurea.muninn.capture.web.x

fun classifiedXTags(
    payload: XCapturePayload
): List<String> =
    buildList {
        addAll(
            payload.tags
        )

        if (
            payload.isSensitive
        ) {
            add(
                "Sensitive"
            )
        }
    }
        .map {
            it.trim()
        }
        .filter {
            it.isNotBlank()
        }
        .distinct()