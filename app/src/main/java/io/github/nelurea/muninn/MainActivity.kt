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
import io.github.nelurea.muninn.ui.navigation.AppNavigation
import io.github.nelurea.muninn.ui.theme.MuninnTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

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
                AppNavigation()
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
                "muninn_${System.currentTimeMillis()}.jpg"
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
    }
}