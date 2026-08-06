package io.github.nelurea.muninn.capture

import io.github.nelurea.muninn.data.repository.PendingCaptureRepository
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository

class CaptureResolver(
    private val pendingRepository: PendingCaptureRepository,
    private val resolvedRepository: ResolvedCaptureRepository
) {

    suspend fun resolveAll() {

        val pendingCaptures =
            pendingRepository.getAll()

        pendingCaptures.forEach { pending ->

            val request =
                CaptureRequest(
                    sourceUrl = pending.sourceUrl,
                    imageIndex = pending.imageIndex
                )

            val resolved =
                UrlResolver.resolve(request)
                    ?: return@forEach

            resolvedRepository.save(resolved)
        }
    }
}