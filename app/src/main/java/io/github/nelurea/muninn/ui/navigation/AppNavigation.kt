package io.github.nelurea.muninn.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import io.github.nelurea.muninn.ui.screen.DetailScreen
import io.github.nelurea.muninn.ui.screen.GalleryScreen
import io.github.nelurea.muninn.ui.screen.HomeScreen
import io.github.nelurea.muninn.ui.screen.SettingsScreen
import io.github.nelurea.muninn.data.repository.ImageRepository

@Composable
fun AppNavigation(
    repository: ImageRepository
) {

    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        composable("home") {
            HomeScreen(
                onGalleryClick = {
                    navController.navigate("gallery")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("gallery") {
            GalleryScreen(
                repository = repository,
                onBack = {
                    navController.popBackStack()
                },
                onImageClick = { imageId ->
                    navController.navigate(
                        "detail/$imageId"
                    )
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "detail/{imageId}",
            arguments = listOf(
                navArgument("imageId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->

            val imageId =
                backStackEntry.arguments
                    ?.getLong("imageId")
                    ?: return@composable

            DetailScreen(
                imageId = imageId,
                repository = repository,
                onDelete = {
                    navController.popBackStack()
                },
                onShare = { uri ->

                    val shareIntent = Intent(
                        Intent.ACTION_SEND
                    ).apply {

                        type = "image/*"

                        putExtra(
                            Intent.EXTRA_STREAM,
                            Uri.parse(uri)
                        )

                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            "Share Image"
                        )
                    )
                }
            )
        }
    }
}