package io.github.nelurea.muninn.debug.capture

import android.util.Log
import io.github.nelurea.muninn.data.repository.PendingCaptureRepository

object PendingCaptureInspector {

    suspend fun inspect(
        repository: PendingCaptureRepository
    ) {

        val captures =
            repository.getAll()

        Log.d(
            "Muninn",
            "pendingCaptureCount=${captures.size}"
        )

        captures.forEachIndexed { index, capture ->

            Log.d(
                "Muninn",
                "pendingCapture[$index]=$capture"
            )
        }
    }
}