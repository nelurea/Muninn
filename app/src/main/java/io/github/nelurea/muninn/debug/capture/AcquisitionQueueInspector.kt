package io.github.nelurea.muninn.debug.capture

import android.util.Log
import io.github.nelurea.muninn.data.repository.AcquisitionQueueRepository

object AcquisitionQueueInspector {

    suspend fun inspect(
        repository: AcquisitionQueueRepository
    ) {

        val list = repository.getAll()

        Log.d(
            "Muninn",
            "acquisitionQueueCount=${list.size}"
        )

        list.forEachIndexed { index, item ->

            Log.d(
                "Muninn",
                "acquisitionQueue[$index]=$item"
            )
        }
    }
}