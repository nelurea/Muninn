package io.github.nelurea.muninn

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import io.github.nelurea.muninn.capture.PendingCaptureResolver
import io.github.nelurea.muninn.capture.ShareUrlExtractor
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.repository.AcquisitionQueueRepository
import io.github.nelurea.muninn.data.repository.CaptureEventRepository
import io.github.nelurea.muninn.data.repository.ImageRepository
import io.github.nelurea.muninn.data.repository.PendingCaptureRepository
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository
import io.github.nelurea.muninn.data.repository.SessionRepository
import io.github.nelurea.muninn.debug.capture.AcquisitionQueueInspector
import io.github.nelurea.muninn.debug.capture.CaptureEventInspector
import io.github.nelurea.muninn.debug.capture.PendingCaptureInspector
import io.github.nelurea.muninn.debug.capture.ResolvedCaptureInspector
import io.github.nelurea.muninn.debug.observation.ShareIntentInspector
import io.github.nelurea.muninn.ui.navigation.AppNavigation
import io.github.nelurea.muninn.ui.theme.MuninnTheme
import kotlinx.coroutines.launch
import io.github.nelurea.muninn.data.db.MIGRATION_8_9

class MainActivity : ComponentActivity() {

    private lateinit var repository: ImageRepository
    private lateinit var sessionRepository: SessionRepository

    private lateinit var pendingCaptureRepository: PendingCaptureRepository
    private lateinit var resolvedCaptureRepository: ResolvedCaptureRepository

    private lateinit var acquisitionQueueRepository: AcquisitionQueueRepository

    private lateinit var captureEventRepository: CaptureEventRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "muninn.db"
        )
            .addMigrations(MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()

        repository = ImageRepository(
            dao = database.imageRecordDao(),
            context = applicationContext
        )

        sessionRepository = SessionRepository(
            dao = database.sessionDao()
        )

        pendingCaptureRepository =
            PendingCaptureRepository(
                database.pendingCaptureDao()
            )

        resolvedCaptureRepository =
            ResolvedCaptureRepository(
                database.resolvedCaptureDao()
            )

        acquisitionQueueRepository =
            AcquisitionQueueRepository(
                database.acquisitionQueueDao()
            )

        captureEventRepository =
            CaptureEventRepository(
                database.captureEventDao()
            )

        val pendingCaptureResolver =
            PendingCaptureResolver(
                pendingCaptureRepository,
                resolvedCaptureRepository,
                acquisitionQueueRepository,
                captureEventRepository
            )

        enableEdgeToEdge()

        ShareIntentInspector.inspect(intent)

        ShareUrlExtractor.extract(intent)
            ?.let { request ->

                lifecycleScope.launch {

                    pendingCaptureRepository.save(
                        sourceUrl = request.sourceUrl,
                        imageIndex = request.imageIndex
                    )

                    pendingCaptureResolver.resolveAll()

                    PendingCaptureInspector.inspect(
                        pendingCaptureRepository
                    )

                    ResolvedCaptureInspector.inspect(
                        resolvedCaptureRepository
                    )

                    AcquisitionQueueInspector.inspect(
                        acquisitionQueueRepository
                    )
                }
            }

        lifecycleScope.launch {

            pendingCaptureResolver.resolveAll()
            CaptureEventInspector.inspect(
                captureEventRepository
            )

            PendingCaptureInspector.inspect(
                pendingCaptureRepository
            )

            ResolvedCaptureInspector.inspect(
                resolvedCaptureRepository
            )

            AcquisitionQueueInspector.inspect(
                acquisitionQueueRepository
            )
        }

        setContent {
            MuninnTheme {
                AppNavigation(
                    repository = repository,
                    sessionRepository = sessionRepository,
                    resolvedCaptureRepository =
                        resolvedCaptureRepository
                )
            }
        }
    }

    private fun saveImage(imageUri: Uri) {

        val inputStream =
            contentResolver.openInputStream(imageUri)
                ?: return

        val values = ContentValues().apply {

            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "${System.currentTimeMillis()}.jpg"
            )

            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/Muninn"
            )
        }

        val outputUri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return

        val outputStream =
            contentResolver.openOutputStream(outputUri)
                ?: return

        inputStream.copyTo(outputStream)

        inputStream.close()
        outputStream.close()

        Log.d("Muninn", "Saved image: $outputUri")

        lifecycleScope.launch {

            val sessionId =
                sessionRepository.getOrCreateSession()

            repository.save(
                outputUri.toString(),
                sessionId
            )

            sessionRepository.touch(
                sessionId
            )
        }
    }
}