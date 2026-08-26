package io.github.nelurea.muninn.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nelurea.muninn.capture.discovery.PixivDiscoverySaveUseCase
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.data.db.StateVocabularyEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import io.github.nelurea.muninn.data.repository.ImageRepository
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository
import io.github.nelurea.muninn.data.repository.SessionRepository
import io.github.nelurea.muninn.discovery.DiscoveryViewModel
import io.github.nelurea.muninn.discovery.model.DiscoverySourceId
import io.github.nelurea.muninn.discovery.pixiv.PixivArtworkPreviewSource
import io.github.nelurea.muninn.discovery.pixiv.PixivDiscoverySource
import io.github.nelurea.muninn.ui.capture.CapturedWorkDetailScreen
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
import io.github.nelurea.muninn.ui.session.SessionStatePicker
import kotlinx.coroutines.launch
import io.github.nelurea.muninn.discovery.x.XArtworkPreviewSource
import io.github.nelurea.muninn.discovery.x.XDiscoverySource
import io.github.nelurea.muninn.capture.discovery.XDiscoverySaveUseCase
import androidx.compose.runtime.DisposableEffect
import io.github.nelurea.muninn.capture.discovery.DiscoverySaveCoordinator
import io.github.nelurea.muninn.capture.storage.UserSelectedMediaStorage
import io.github.nelurea.muninn.media.move.MediaMoveBatchCoordinator

@Composable
fun AppNavigation(
    repository: ImageRepository,
    sessionRepository: SessionRepository,
    resolvedCaptureRepository: ResolvedCaptureRepository,
    capturedWorkRepository: CapturedWorkRepository,
    mediaMoveBatchCoordinator: MediaMoveBatchCoordinator
) {
    val navController =
        rememberNavController()

    val context =
        LocalContext.current

    val migrationScope =
        rememberCoroutineScope()

    var pendingInitialPreview by remember {
        mutableStateOf<Pair<Long, String?>?>(
            null
        )
    }

    val saveCaptureUseCase =
        remember {
            SaveCaptureUseCase(
                mediaStorage =
                    UserSelectedMediaStorage(
                        context.applicationContext
                    ),
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

    val xDiscoverySaveUseCase =
        remember {
            XDiscoverySaveUseCase(
                context =
                    context.applicationContext,
                saveCaptureUseCase =
                    saveCaptureUseCase
            )
        }

    val discoverySaveCoordinator =
        remember {
            DiscoverySaveCoordinator(
                saveUseCases =
                    mapOf(
                        DiscoverySourceId.PIXIV to
                                pixivDiscoverySaveUseCase,

                        DiscoverySourceId.X to
                                xDiscoverySaveUseCase
                    )
            )
        }

    DisposableEffect(
        discoverySaveCoordinator
    ) {
        onDispose {
            discoverySaveCoordinator
                .close()
        }
    }

    val discoveryViewModel =
        remember {
            DiscoveryViewModel(
                sources =
                    mapOf(
                        DiscoverySourceId.PIXIV to
                                PixivDiscoverySource(),

                        DiscoverySourceId.X to
                                XDiscoverySource()
                    ),
                previewSources =
                    mapOf(
                        DiscoverySourceId.PIXIV to
                                PixivArtworkPreviewSource(
                        context =
                            context.applicationContext
                    ),

                        DiscoverySourceId.X to
                                XArtworkPreviewSource()
                    ),
                saveCoordinator =
                    discoverySaveCoordinator
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
                        "gallery"
                    )
                },
                onDiscoveryClick = {
                    navController.navigate(
                        "discovery"
                    )
                },

                onSettingsClick = {
                    navController.navigate(
                        "settings"
                    )
                },
                onSessionsClick = {
                    navController.navigate(
                        "sessions"
                    )
                }
            )
        }

        composable(
            "gallery"
        ) {
            GalleryScreen(
                repository =
                    capturedWorkRepository,
                onWorkClick = {
                        workId,
                        initialPreviewUri ->

                    pendingInitialPreview =
                        workId to initialPreviewUri

                    navController.navigate(
                        "capturedWorkDetail/$workId"
                    )
                }
            )
        }

        composable(
            route =
                "capturedWorkDetail/{workId}",
            arguments =
                listOf(
                    navArgument(
                        "workId"
                    ) {
                        type =
                            NavType.LongType
                    }
                )
        ) {
                backStackEntry ->

            val workId =
                backStackEntry
                    .arguments
                    ?.getLong(
                        "workId"
                    )
                    ?: return@composable

            val initialPreviewUri =
                remember(
                    backStackEntry,
                    workId
                ) {
                    pendingInitialPreview
                        ?.takeIf {
                            it.first == workId
                        }
                        ?.second
                }

            LaunchedEffect(
                backStackEntry,
                workId
            ) {
                if (
                    pendingInitialPreview
                        ?.first == workId
                ) {
                    pendingInitialPreview =
                        null
                }
            }

            CapturedWorkDetailScreen(
                workId =
                    workId,
                initialPreviewUri =
                    initialPreviewUri,
                repository =
                    capturedWorkRepository
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
            val xSessionState =
                remember {
                    io.github.nelurea.muninn.discovery.x.XWebSessionState()
                }

            val xUserId =
                remember {
                    androidx.compose.runtime.mutableStateOf(
                        xSessionState
                            .getAuthenticatedUserId()
                    )
                }

            val xAccountLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                        androidx.activity.result.contract.ActivityResultContracts
                            .StartActivityForResult()
                ) {
                    val previousUserId =
                        xUserId.value

                    val currentUserId =
                        xSessionState
                            .getAuthenticatedUserId()

                    xUserId.value =
                        currentUserId

                    if (
                        previousUserId !=
                        currentUserId
                    ) {
                        discoveryViewModel
                            .notifyXAccountChanged()
                    }
                }

            SettingsScreen(
                mediaMoveBatchCoordinator =
                    mediaMoveBatchCoordinator,
                migrationScope =
                    migrationScope,
                onBack = {
                    navController
                        .popBackStack()
                },
                onResolvedCapturesClick = {
                    navController.navigate(
                        "resolvedCaptures"
                    )
                },
                xUserId =
                    xUserId.value,
                onXLoginClick = {
                    xAccountLauncher.launch(
                        io.github.nelurea.muninn.ui.browser.XLoginActivity
                            .createLoginIntent(
                                context
                            )
                    )
                },
                onXSwitchAccountClick = {
                    xAccountLauncher.launch(
                        io.github.nelurea.muninn.ui.browser.XLoginActivity
                            .createSwitchAccountIntent(
                                context
                            )
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
            val scope =
                rememberCoroutineScope()

            val pixivLoginLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts
                        .StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        discoveryViewModel.retry()
                    }
                }

            var showStatePicker by remember {
                mutableStateOf(
                    false
                )
            }

            var activeSessionId by remember {
                mutableStateOf<Long?>(
                    null
                )
            }

            var stateVocabulary by remember {
                mutableStateOf<
                        List<StateVocabularyEntity>
                        >(
                    emptyList()
                )
            }

            var selectedStateIds by remember {
                mutableStateOf<
                        Set<Long>
                        >(
                    emptySet()
                )
            }

            var newStateLabel by remember {
                mutableStateOf(
                    ""
                )
            }

            LaunchedEffect(
                Unit
            ) {
                val resolution =
                    sessionRepository
                        .resolveSession()

                activeSessionId =
                    resolution.sessionId

                stateVocabulary =
                    sessionRepository
                        .getStateVocabulary()

                if (
                    resolution.isNew
                ) {
                    showStatePicker =
                        true
                }
            }

            DiscoveryScreen(
                viewModel =
                    discoveryViewModel,
                onPixivLogin = {
                    pixivLoginLauncher.launch(
                        io.github.nelurea.muninn.ui.browser.PixivLoginActivity
                            .createIntent(context)
                    )
                },
                onItemClick = {
                        item ->

                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                item.canonicalUrl
                            )
                        )

                    context.startActivity(
                        intent
                    )
                },
                onBack = {
                    navController
                        .popBackStack()
                }
            )

            if (
                showStatePicker &&
                activeSessionId != null
            ) {
                SessionStatePicker(
                    vocabulary =
                        stateVocabulary,
                    selectedStateIds =
                        selectedStateIds,
                    newStateLabel =
                        newStateLabel,
                    onNewStateLabelChange = {
                        newStateLabel =
                            it
                    },
                    onToggleState = {
                            state ->

                        val sessionId =
                            activeSessionId
                                ?: return@SessionStatePicker

                        if (
                            state.id in
                            selectedStateIds
                        ) {
                            selectedStateIds =
                                selectedStateIds -
                                        state.id

                            scope.launch {
                                sessionRepository
                                    .removeStateFromSession(
                                        sessionId =
                                            sessionId,
                                        stateVocabularyId =
                                            state.id
                                    )
                            }
                        } else {
                            selectedStateIds =
                                selectedStateIds +
                                        state.id

                            scope.launch {
                                sessionRepository
                                    .addStateToSession(
                                        sessionId =
                                            sessionId,
                                        label =
                                            state.label
                                    )
                            }
                        }
                    },
                    onAddState = {
                        val sessionId =
                            activeSessionId
                                ?: return@SessionStatePicker

                        val label =
                            newStateLabel
                                .trim()

                        if (
                            label.isNotBlank()
                        ) {
                            scope.launch {
                                sessionRepository
                                    .addStateToSession(
                                        sessionId =
                                            sessionId,
                                        label =
                                            label
                                    )

                                stateVocabulary =
                                    sessionRepository
                                        .getStateVocabulary()

                                selectedStateIds =
                                    sessionRepository
                                        .getStatesForSession(
                                            sessionId
                                        )
                                        .map {
                                            it.id
                                        }
                                        .toSet()

                                newStateLabel =
                                    ""
                            }
                        }
                    },
                    onDone = {
                        showStatePicker =
                            false
                    },
                    onSkip = {
                        showStatePicker =
                            false
                    }
                )
            }
        }


    }
}
