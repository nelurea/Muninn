package io.github.nelurea.muninn.ui.navigation

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
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}