package io.github.nelurea.muninn.capturepackage.transport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.capturepackage.CapturePackageImportResult
import io.github.nelurea.muninn.capturepackage.CapturePackageImporter
import io.github.nelurea.muninn.data.db.AppDatabaseProvider
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
class CapturePackageReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) {
            Log.w(
                TAG,
                "Unsupported action: ${intent.action}"
            )
            finish()
            return
        }

        val streamUri = extractStreamUri(intent)

        if (streamUri == null) {
            Log.w(
                TAG,
                "ACTION_SEND did not contain a file URI"
            )
            finish()
            return
        }

        val hasReadGrant =
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0

        Log.i(
            TAG,
            """
            Received shared file:
              action=${intent.action}
              type=${intent.type}
              uri=$streamUri
              scheme=${streamUri.scheme}
              readGrant=$hasReadGrant
              flags=${intent.flags}
            """.trimIndent()
        )

        lifecycleScope.launch {
            var copiedFile: File? = null
            var extractedDirectory: File? = null

            try {
                val importResult =
                    withContext(Dispatchers.IO) {
                        copiedFile =
                            copyToTemporaryFile(
                                streamUri
                            )

                        Log.i(
                            TAG,
                            """
                            Shared file copied:
                              path=${copiedFile!!.absolutePath}
                              size=${copiedFile!!.length()}
                              exists=${copiedFile!!.exists()}
                            """.trimIndent()
                        )

                        val extractor =
                            CapturePackageArchiveExtractor(
                                cacheDirectory =
                                    File(
                                        cacheDir,
                                        "capture_transport/extracted"
                                    ).apply {
                                        mkdirs()
                                    }
                            )

                        extractedDirectory =
                            extractor.extract(
                                copiedFile!!
                            )

                        Log.i(
                            TAG,
                            """
                            CapturePackage archive extracted:
                              directory=${extractedDirectory!!.absolutePath}
                              entries=${extractedDirectory!!.walkTopDown().count() - 1}
                            """.trimIndent()
                        )

                        val database =
                            AppDatabaseProvider.get(
                                applicationContext
                            )

                        val repository =
                            CapturedWorkRepository(
                                dao = database.capturedWorkDao()
                            )

                        val sessionRepository =
                            SessionRepository(
                                dao = database.sessionDao()
                            )

                        val saveCaptureUseCase = SaveCaptureUseCase(
                            context = this@CapturePackageReceiveActivity,
                            repository = repository,
                            sessionRepository = sessionRepository
                        )

                        val importer = CapturePackageImporter(
                            saveCaptureUseCase = saveCaptureUseCase
                        )

                        importer.import(
                            extractedDirectory!!
                        )
                    }

                when (importResult) {
                    is CapturePackageImportResult.Success -> {
                        Log.i(
                            TAG,
                            """
                            CapturePackage import succeeded:
                              workId=${importResult.workId}
                              mediaCount=${importResult.mediaCount}
                            """.trimIndent()
                        )
                    }

                    is CapturePackageImportResult.Failure -> {
                        Log.e(
                            TAG,
                            """
                            CapturePackage import failed:
                              ${importResult.errors.joinToString("\n  ")}
                            """.trimIndent()
                        )
                    }
                }
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "Failed to process shared CapturePackage",
                    error
                )
            } finally {
                withContext(Dispatchers.IO) {
                    extractedDirectory
                        ?.deleteRecursively()

                    copiedFile
                        ?.delete()
                }

                finish()
            }
        }
    }

    private fun copyToTemporaryFile(
        uri: Uri
    ): File {
        val transportDirectory =
            File(
                cacheDir,
                "capture_transport"
            ).apply {
                mkdirs()
            }

        val destination =
            File(
                transportDirectory,
                "${UUID.randomUUID()}.zip"
            )

        contentResolver
            .openInputStream(uri)
            ?.use { input ->
                destination
                    .outputStream()
                    .use { output ->
                        input.copyTo(output)
                    }
            }
            ?: error(
                "Unable to open input stream for URI: $uri"
            )

        return destination
    }

    private fun extractStreamUri(
        intent: Intent
    ): Uri? {
        val clipData =
            intent.clipData

        if (
            clipData != null &&
            clipData.itemCount > 0
        ) {
            clipData
                .getItemAt(0)
                .uri
                ?.let {
                    return it
                }
        }

        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(
            Intent.EXTRA_STREAM
        )
    }

    companion object {
        private const val TAG =
            "MuninnCaptureTransport"
    }
}