package io.github.nelurea.muninn.debug.capture

import android.content.Context
import android.util.Log
import io.github.nelurea.muninn.capturepackage.CapturePackageImportResult
import io.github.nelurea.muninn.capturepackage.CapturePackageImporter
import io.github.nelurea.muninn.data.db.CapturedWorkDao
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import java.io.File

object CapturePackageImportInspector {

    private const val TAG = "MuninnCaptureImport"

    suspend fun run(
        context: Context,
        dao: CapturedWorkDao
    ) {
        val packageDirectory = File(
            context.filesDir,
            "import-test"
        )

        Log.d(
            TAG,
            "Import directory: ${packageDirectory.absolutePath}"
        )

        val marker = File(packageDirectory, ".imported")

        if (marker.exists()) {
            Log.d(TAG, "Package already imported")
            return
        }

        val importer = CapturePackageImporter(
            context = context,
            repository = CapturedWorkRepository(dao)
        )

        when (val result = importer.import(packageDirectory)) {
            is CapturePackageImportResult.Success -> {
                marker.writeText(result.workId.toString())

                Log.d(
                    TAG,
                    "Import succeeded: workId=${result.workId}, mediaCount=${result.mediaCount}"
                )
            }

            is CapturePackageImportResult.Failure -> {
                Log.e(
                    TAG,
                    "Import failed: ${result.errors.joinToString()}"
                )
            }
        }
    }
}