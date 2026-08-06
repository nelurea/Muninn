package io.github.nelurea.muninn.capture

import android.util.Log
import io.github.nelurea.muninn.data.repository.PendingCaptureRepository
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository

class PendingCaptureResolver(
    private val pendingRepository: PendingCaptureRepository,
    private val resolvedRepository: ResolvedCaptureRepository
) {

    suspend fun resolveAll() {

        val pendingCaptures =
            pendingRepository.getAll()

        pendingCaptures.forEach { pending ->

            if (
                resolvedRepository.isResolved(
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

            resolvedRepository.save(
                pending.id,
                resolved
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