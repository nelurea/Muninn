package io.github.nelurea.muninn

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import io.github.nelurea.muninn.data.db.AppDatabase
import io.github.nelurea.muninn.data.repository.ImageRepository
import io.github.nelurea.muninn.data.repository.SessionRepository
import io.github.nelurea.muninn.debug.observation.ShareIntentInspector
import io.github.nelurea.muninn.ui.navigation.AppNavigation
import io.github.nelurea.muninn.ui.theme.MuninnTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: ImageRepository
    private lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "muninn.db"
        ).build()

        repository = ImageRepository(
            dao = database.imageRecordDao(),
            context = applicationContext
        )

        sessionRepository = SessionRepository(
            dao = database.sessionDao()
        )

        enableEdgeToEdge()

        ShareIntentInspector.inspect(intent)

        if (intent?.action == Intent.ACTION_SEND) {

            val imageUri =
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            if (imageUri != null) {

                Log.d("Muninn", "Shared image: $imageUri")

                saveImage(imageUri)
            }
        }

        setContent {
            MuninnTheme {
                AppNavigation(
                    repository = repository,
                    sessionRepository = sessionRepository
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