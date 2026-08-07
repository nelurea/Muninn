package io.github.nelurea.muninn.capture

import android.util.Log
import io.github.nelurea.muninn.data.repository.AcquisitionQueueRepository
import io.github.nelurea.muninn.data.repository.CaptureEventRepository
import io.github.nelurea.muninn.data.repository.PendingCaptureRepository
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository

class PendingCaptureResolver(

    private val pendingCaptureRepository:
    PendingCaptureRepository,

    private val resolvedCaptureRepository:
    ResolvedCaptureRepository,

    private val acquisitionQueueRepository:
    AcquisitionQueueRepository,

    private val captureEventRepository:
    CaptureEventRepository

) {

    suspend fun resolveAll() {

        val pendingCaptures =
            pendingCaptureRepository.getAll()

        pendingCaptures.forEach { pending ->

            if (
                resolvedCaptureRepository.isResolved(
                    pending.id
                )
            ) {
                Log.d(
                    TAG,
                    "Skipping already resolved capture: ${pending.id}"
                )
                return@forEach
            }

            val request =
                CaptureRequest(
                    sourceUrl = pending.sourceUrl,
                    imageIndex = pending.imageIndex
                )

            val resolved =
                UrlResolver.resolve(request)
                    ?: run {

                        Log.d(
                            TAG,
                            "Unsupported URL: ${pending.sourceUrl}"
                        )

                        return@forEach
                    }

            if (
                resolvedCaptureRepository.exists(
                    resolved
                )
            ) {

                captureEventRepository.save(
                    sourceType = resolved.sourceType.name,
                    sourceId = resolved.sourceId,
                    imageIndex = resolved.imageIndex
                )

                Log.d(
                    TAG,
                    "Duplicate capture detected: ${resolved.sourceId}"
                )

                return@forEach
            }

            val resolvedCaptureId =
                resolvedCaptureRepository.save(
                    pending.id,
                    resolved
                )

            captureEventRepository.save(
                sourceType = resolved.sourceType.name,
                sourceId = resolved.sourceId,
                imageIndex = resolved.imageIndex
            )

            acquisitionQueueRepository.enqueue(
                resolvedCaptureId
            )

            Log.d(
                TAG,
                "Resolved capture: ${pending.id}"
            )
        }
    }

    companion object {

        private const val TAG =
            "PendingCaptureResolver"
    }
}