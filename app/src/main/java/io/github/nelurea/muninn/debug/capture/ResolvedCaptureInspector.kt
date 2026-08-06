package io.github.nelurea.muninn.debug.capture

import android.util.Log
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository

object ResolvedCaptureInspector {

    suspend fun inspect(
        repository: ResolvedCaptureRepository
    ) {

        val captures =
            repository.getAll()

        Log.d(
            "Muninn",
            "resolvedCaptureCount=${captures.size}"
        )

        captures.forEachIndexed { index, capture ->

            Log.d(
                "Muninn",
                "resolvedCapture[$index]=$capture"
            )
        }
    }
}