package io.github.nelurea.muninn.capture

import io.github.nelurea.muninn.data.db.PendingCaptureEntity

fun CaptureRequest.toPendingCaptureEntity() =
    PendingCaptureEntity(
        sourceUrl = sourceUrl,
        imageIndex = imageIndex,
        createdAt = System.currentTimeMillis()
    )