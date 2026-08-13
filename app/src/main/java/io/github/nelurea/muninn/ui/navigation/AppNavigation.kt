package io.github.nelurea.muninn.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.data.repository.ImageRepository
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository
import io.github.nelurea.muninn.data.repository.SessionRepository
import io.github.nelurea.muninn.discovery.DiscoveryViewModel
import io.github.nelurea.muninn.discovery.pixiv.PixivDiscoverySource
import io.github.nelurea.muninn.ui.browser.WebCaptureScreen
import io.github.nelurea.muninn.ui.capture.ResolvedCaptureScreen
import io.github.nelurea.muninn.ui.capture.ResolvedCaptureViewModel
import io.github.nelurea.muninn.ui.discovery.DiscoveryScreen
import io.github.nelurea.muninn.ui.screen.DetailScreen
import io.github.nelurea.muninn.ui.screen.GalleryScreen
import io.github.nelurea.muninn.ui.screen.HomeScreen
import io.github.nelurea.muninn.ui.screen.SettingsScreen
import io.github.nelurea.muninn.ui.session.SessionDetailScreen
import io.github.nelurea.muninn.ui.session.SessionDetailViewModel
import io.github.nelurea.muninn.ui.session.SessionListScreen
import io.github.nelurea.muninn.ui.session.SessionListViewModel
import io.github.nelurea.muninn.discovery.pixiv.PixivArtworkPreviewSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import io.github.nelurea.muninn.capture.discovery.PixivDiscoverySaveUseCase

@Composable
fun AppNavigation(
    repository: ImageRepository,
    sessionRepository: SessionRepository,
    resolvedCaptureRepository: ResolvedCaptureRepository,
    capturedWorkRepository: CapturedWorkRepository
) {
    val navController =
        rememberNavController()

    val context =
        LocalContext.current

    val saveCaptureUseCase =
        remember {
            SaveCaptureUseCase(
                context =
                    context.applicationContext,
                repository =
                    capturedWorkRepository,
                sessionRepository =
                    sessionRepository
            )
        }

    val pixivDiscoverySaveUseCase =
        remember {
            PixivDiscoverySaveUseCase(
                context =
                    context.applicationContext,
                saveCaptureUseCase =
                    saveCaptureUseCase
            )
        }

    val discoveryViewModel =
        remember {
            DiscoveryViewModel(
                source =
                    PixivDiscoverySource(),
                previewSource =
                    PixivArtworkPreviewSource(),
                saveUseCase =
                    pixivDiscoverySaveUseCase
            )
        }

    NavHost(
        navController =
            navController,
        startDestination =
            "home",
        modifier =
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing
            )
    ) {
        composable(
            "home"
        ) {
            HomeScreen(
                onGalleryClick = {
                    navController.navigate(
                        "sessions"
                    )
                },
                onDiscoveryClick = {
                    navController.navigate(
                        "discovery"
                    )
                },
                onWebCaptureClick = {
                    navController.navigate(
                        "webCapture"
                    )
                },
                onSettingsClick = {
                    navController.navigate(
                        "settings"
                    )
                }
            )
        }

        composable(
            "gallery"
        ) {
            GalleryScreen(
                repository =
                    repository,
                onBack = {
                    navController
                        .popBackStack()
                },
                onImageClick = {
                        imageId ->

                    navController.navigate(
                        "detail/$imageId"
                    )
                }
            )
        }

        composable(
            "sessions"
        ) {
            val vm =
                remember {
                    SessionListViewModel(
                        sessionRepository
                    )
                }

            SessionListScreen(
                viewModel =
                    vm,
                onSessionClick = {
                        sessionId ->

                    navController.navigate(
                        "sessionDetail/$sessionId"
                    )
                }
            )
        }

        composable(
            route =
                "sessionDetail/{sessionId}",
            arguments =
                listOf(
                    navArgument(
                        "sessionId"
                    ) {
                        type =
                            NavType.LongType
                    }
                )
        ) {
                backStackEntry ->

            val sessionId =
                backStackEntry
                    .arguments
                    ?.getLong(
                        "sessionId"
                    )
                    ?: return@composable

            val vm =
                remember {
                    SessionDetailViewModel(
                        sessionRepository
                    )
                }

            SessionDetailScreen(
                sessionId =
                    sessionId,
                viewModel =
                    vm
            )
        }

        composable(
            "settings"
        ) {
            SettingsScreen(
                onBack = {
                    navController
                        .popBackStack()
                },
                onResolvedCapturesClick = {
                    navController.navigate(
                        "resolvedCaptures"
                    )
                }
            )
        }

        composable(
            route =
                "resolvedCaptures"
        ) {
            val vm =
                remember {
                    ResolvedCaptureViewModel(
                        resolvedCaptureRepository
                    )
                }

            ResolvedCaptureScreen(
                viewModel =
                    vm,
                onBack = {
                    navController
                        .popBackStack()
                }
            )
        }

        composable(
            route =
                "detail/{imageId}",
            arguments =
                listOf(
                    navArgument(
                        "imageId"
                    ) {
                        type =
                            NavType.LongType
                    }
                )
        ) {
                backStackEntry ->

            val imageId =
                backStackEntry
                    .arguments
                    ?.getLong(
                        "imageId"
                    )
                    ?: return@composable

            DetailScreen(
                imageId =
                    imageId,
                repository =
                    repository,
                onDelete = {
                    navController
                        .popBackStack()
                },
                onShare = {
                        uri ->

                    val shareIntent =
                        Intent(
                            Intent.ACTION_SEND
                        ).apply {
                            type =
                                "image/*"

                            putExtra(
                                Intent.EXTRA_STREAM,
                                Uri.parse(
                                    uri
                                )
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

        composable(
            route =
                "discovery"
        ) {
            DiscoveryScreen(
                viewModel =
                    discoveryViewModel,
                onItemClick = {
                        item ->

                    val discoveryMode =
                        discoveryViewModel
                            .mode
                            .name

                    val discoveryQuery =
                        discoveryViewModel
                            .searchQuery
                            .takeIf {
                                discoveryViewModel
                                    .mode
                                    .name ==
                                        "SEARCH" &&
                                        it.isNotBlank()
                            }

                    navController.navigate(
                        buildString {
                            append(
                                "webCapture?url="
                            )

                            append(
                                Uri.encode(
                                    item.canonicalUrl
                                )
                            )

                            append(
                                "&discoveryMode="
                            )

                            append(
                                Uri.encode(
                                    discoveryMode
                                )
                            )

                            discoveryQuery
                                ?.let {
                                        query ->

                                    append(
                                        "&discoveryQuery="
                                    )

                                    append(
                                        Uri.encode(
                                            query
                                        )
                                    )
                                }
                        }
                    )
                },
                onBack = {
                    navController
                        .popBackStack()
                }
            )
        }

        composable(
            route =
                "webCapture"
        ) {
            WebCaptureScreen(
                saveCaptureUseCase =
                    saveCaptureUseCase,
                initialUrl =
                    "https://www.pixiv.net/",
                onBack = {
                    navController
                        .popBackStack()
                }
            )
        }

        composable(
            route =
                "webCapture?url={url}" +
                        "&discoveryMode={discoveryMode}" +
                        "&discoveryQuery={discoveryQuery}",
            arguments =
                listOf(
                    navArgument(
                        "url"
                    ) {
                        type =
                            NavType.StringType

                        nullable =
                            true

                        defaultValue =
                            null
                    },

                    navArgument(
                        "discoveryMode"
                    ) {
                        type =
                            NavType.StringType

                        nullable =
                            true

                        defaultValue =
                            null
                    },

                    navArgument(
                        "discoveryQuery"
                    ) {
                        type =
                            NavType.StringType

                        nullable =
                            true

                        defaultValue =
                            null
                    }
                )
        ) {
                backStackEntry ->

            val initialUrl =
                backStackEntry
                    .arguments
                    ?.getString(
                        "url"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "https://www.pixiv.net/"

            val discoveryMode =
                backStackEntry
                    .arguments
                    ?.getString(
                        "discoveryMode"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }

            val discoveryQuery =
                backStackEntry
                    .arguments
                    ?.getString(
                        "discoveryQuery"
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }

            WebCaptureScreen(
                saveCaptureUseCase =
                    saveCaptureUseCase,
                initialUrl =
                    initialUrl,
                discoveryMode =
                    discoveryMode,
                discoveryQuery =
                    discoveryQuery,
                onBack = {
                    navController
                        .popBackStack()
                }
            )
        }
    }
}