package io.github.nelurea.muninn

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.nelurea.muninn.capture.PendingCaptureResolver
import io.github.nelurea.muninn.capture.ShareUrlExtractor
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.db.AppDatabaseProvider
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
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.media.move.AndroidMediaMoveFileOperations
import io.github.nelurea.muninn.media.move.MediaMoveBatchCoordinator
import io.github.nelurea.muninn.media.move.MediaMoveRepository
import io.github.nelurea.muninn.media.move.MediaMoveService
import io.github.nelurea.muninn.legacy.LegacyMediaStoreImporter
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var repository: ImageRepository
    private lateinit var sessionRepository: SessionRepository

    private lateinit var pendingCaptureRepository: PendingCaptureRepository
    private lateinit var resolvedCaptureRepository: ResolvedCaptureRepository

    private lateinit var acquisitionQueueRepository: AcquisitionQueueRepository

    private lateinit var captureEventRepository: CaptureEventRepository

    private lateinit var capturedWorkRepository: CapturedWorkRepository
    private lateinit var mediaMoveBatchCoordinator: MediaMoveBatchCoordinator
    private lateinit var legacyMediaStoreImporter: LegacyMediaStoreImporter

    private val galleryContentRevision = MutableStateFlow(0)
    private val legacyImportInProgress = MutableStateFlow(false)

    private val mediaPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                importLegacyMedia()
            } else {
                Log.w(
                    "Muninn/LegacyMediaStore",
                    "Image access was not granted; legacy gallery recovery is pending"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database =
            AppDatabaseProvider.get(
                applicationContext
            )

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

        capturedWorkRepository =
            CapturedWorkRepository(
                database
            )

        legacyMediaStoreImporter =
            LegacyMediaStoreImporter(
                context = applicationContext,
                repository = capturedWorkRepository
            )

        mediaMoveBatchCoordinator =
            MediaMoveBatchCoordinator(
                MediaMoveService(
                    persistence =
                        MediaMoveRepository(
                            database.mediaMoveDao()
                        ),
                    files =
                        AndroidMediaMoveFileOperations(
                            applicationContext
                        )
                )
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
                        resolvedCaptureRepository,
                    capturedWorkRepository =
                        capturedWorkRepository,
                    mediaMoveBatchCoordinator =
                        mediaMoveBatchCoordinator,
                    galleryContentRevision =
                        galleryContentRevision,
                    legacyImportInProgress =
                        legacyImportInProgress
                )
            }
        }

        recoverLegacyMedia()
    }

    private fun recoverLegacyMedia() {
        val permission = legacyMediaStoreImporter.requiredReadPermission()

        if (permission == null || legacyMediaStoreImporter.hasReadPermission()) {
            importLegacyMedia()
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    private fun importLegacyMedia() {
        legacyImportInProgress.value = true
        lifecycleScope.launch {
            runCatching {
                legacyMediaStoreImporter.import()
            }.onSuccess { result ->
                if (result.completed && result.imported > 0) {
                    galleryContentRevision.value++
                }

                Log.i(
                    "Muninn/LegacyMediaStore",
                    "Scanned ${result.scanned} legacy images; imported ${result.imported}"
                )
            }.onFailure { error ->
                Log.e(
                    "Muninn/LegacyMediaStore",
                    "Legacy gallery recovery failed",
                    error
                )
            }.also {
                legacyImportInProgress.value = false
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
