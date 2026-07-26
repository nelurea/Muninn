package io.github.nelurea.muninn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.nelurea.muninn.ui.navigation.AppNavigation
import io.github.nelurea.muninn.ui.theme.MuninnTheme
import android.content.Intent
import android.net.Uri
import android.util.Log
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == Intent.ACTION_SEND) {

            val imageUri =
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            Log.d("Muninn", "Shared image: $imageUri")
        }
        setContent {
            MuninnTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MuninnTheme {
        Greeting("Android")
    }
}