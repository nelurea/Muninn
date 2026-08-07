package io.github.nelurea.muninn.debug.capture

import android.util.Log
import io.github.nelurea.muninn.data.repository.CaptureEventRepository

object CaptureEventInspector {

    private const val TAG = "Muninn"

    suspend fun inspect(
        repository: CaptureEventRepository
    ) {

        val events =
            repository.getAll()

        Log.d(
            TAG,
            "captureEventCount=${events.size}"
        )

        events.forEachIndexed { index, event ->

            Log.d(
                TAG,
                "captureEvent[$index]=$event"
            )
        }
    }
}