package io.github.nelurea.muninn.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.nelurea.muninn.capture.storage.MediaStorageMode
import io.github.nelurea.muninn.capture.storage.StoragePreferences
import io.github.nelurea.muninn.media.move.MediaMoveBatchCoordinator
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@Composable
fun SettingsScreen(
    mediaMoveBatchCoordinator: MediaMoveBatchCoordinator,
    migrationScope: CoroutineScope,
    onBack: () -> Unit,
    onResolvedCapturesClick: () -> Unit,
    xUserId: String?,
    onXLoginClick: () -> Unit,
    onXSwitchAccountClick: () -> Unit
) {
    val context =
        LocalContext.current

    val migrationState by
        mediaMoveBatchCoordinator.state.collectAsState()

    val storageControlsLocked =
        migrationState.isRunning ||
            migrationState.hasUnfinishedJournal ||
            !migrationState.journalStatusLoaded

    var showMoveConfirmation by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        mediaMoveBatchCoordinator
    ) {
        mediaMoveBatchCoordinator
            .refreshUnfinishedJournal()
    }

    val storagePreferences =
        remember {
            StoragePreferences(
                context.applicationContext
            )
        }

    var selectedTreeUri by remember {
        mutableStateOf(
            storagePreferences
                .getTreeUri()
        )
    }

    var storageMode by remember {
        mutableStateOf(
            storagePreferences
                .getMode()
        )
    }

    var storageError by remember {
        mutableStateOf<String?>(
            null
        )
    }

    val storagePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocumentTree()
        ) {
                uri ->

            if (
                uri != null
            ) {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                runCatching {
                    context
                        .contentResolver
                        .takePersistableUriPermission(
                            uri,
                            flags
                        )

                    storagePreferences
                        .setTreeUri(
                            uri.toString()
                        )

                    storagePreferences
                        .setMode(
                            MediaStorageMode.EXTERNAL
                        )

                    selectedTreeUri =
                        uri.toString()

                    storageMode =
                        MediaStorageMode.EXTERNAL

                    storageError =
                        null
                }.onFailure {
                        exception ->

                    storageError =
                        exception.message
                            ?: "Could not save storage permission."
                }
            }
        }

    Column(
        modifier =
            Modifier
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    16.dp
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        Text(
            "Settings"
        )

        Text(
            "X"
        )

        Text(
            xUserId
                ?.let {
                    "User ID: $it"
                }
                ?: "Not signed in"
        )

        Button(
            onClick =
                if (
                    xUserId == null
                ) {
                    onXLoginClick
                } else {
                    onXSwitchAccountClick
                },
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                if (
                    xUserId == null
                ) {
                    "X login"
                } else {
                    "Switch account"
                }
            )
        }

        Text(
            "Storage location"
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            if (
                storageMode ==
                MediaStorageMode.INTERNAL
            ) {
                Button(
                    onClick = {},
                    enabled =
                        !storageControlsLocked,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        "Internal"
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        storagePreferences
                            .setMode(
                                MediaStorageMode.INTERNAL
                            )

                        storageMode =
                            MediaStorageMode.INTERNAL

                        storageError =
                            null
                    },
                    enabled =
                        !storageControlsLocked,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        "Internal"
                    )
                }
            }

            if (
                storageMode ==
                MediaStorageMode.EXTERNAL
            ) {
                Button(
                    onClick = {},
                    enabled =
                        !storageControlsLocked,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        "External"
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (
                            selectedTreeUri != null
                        ) {
                            storagePreferences
                                .setMode(
                                    MediaStorageMode.EXTERNAL
                                )

                            storageMode =
                                MediaStorageMode.EXTERNAL

                            storageError =
                                null
                        } else {
                            storagePicker.launch(
                                null
                            )
                        }
                    },
                    enabled =
                        !storageControlsLocked,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        "External"
                    )
                }
            }
        }

        Text(
            text =
                when (
                    storageMode
                ) {
                    MediaStorageMode.INTERNAL ->
                        "New captures will use internal app storage."

                    MediaStorageMode.EXTERNAL ->
                        "New captures will use the selected external folder."
                }
        )

        if (
            selectedTreeUri != null
        ) {
            Text(
                "External folder is configured."
            )
        } else {
            Text(
                "No external folder selected."
            )
        }

        OutlinedButton(
            onClick = {
                storagePicker.launch(
                    selectedTreeUri
                        ?.let(
                            Uri::parse
                        )
                )
            },
            enabled =
                !storageControlsLocked,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                if (
                    selectedTreeUri == null
                ) {
                    "Choose external folder"
                } else {
                    "Change external folder"
                }
            )
        }

        storageError
            ?.let {
                    error ->

                Text(
                    text =
                        error
                )
            }

        Button(
            onClick = {
                showMoveConfirmation =
                    true
            },
            enabled =
                !storageControlsLocked &&
                    (
                        storageMode == MediaStorageMode.INTERNAL ||
                            selectedTreeUri != null
                    ),
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Move all captured media here"
            )
        }

        if (
            migrationState.isRunning ||
            migrationState.total > 0
        ) {
            Text(
                "processed: ${migrationState.processed} / total: ${migrationState.total}"
            )
            Text(
                "completed: ${migrationState.completed} / skipped: ${migrationState.skipped} / failed: ${migrationState.failed}"
            )
        }

        if (
            migrationState.failed > 0 &&
            !migrationState.isRunning
        ) {
            OutlinedButton(
                onClick = {
                    migrationScope.launch {
                        mediaMoveBatchCoordinator
                            .retryFailed()
                    }
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    "Retry failed"
                )
            }
        }

        if (
            migrationState.hasUnfinishedJournal &&
            !migrationState.isRunning
        ) {
            OutlinedButton(
                onClick = {
                    migrationScope.launch {
                        mediaMoveBatchCoordinator
                            .resumeIncomplete()
                    }
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    "Resume unfinished move"
                )
            }
        }

        Button(
            onClick =
                onResolvedCapturesClick,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Resolved Captures"
            )
        }

        Button(
            onClick =
                onBack,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Back"
            )
        }
    }

    if (
        showMoveConfirmation
    ) {
        AlertDialog(
            onDismissRequest = {
                showMoveConfirmation =
                    false
            },
            title = {
                Text(
                    "Move captured media?"
                )
            },
            text = {
                Text(
                    "All existing captured media will be moved to the currently selected storage location."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMoveConfirmation =
                            false

                        val destinationSnapshot =
                            when (
                                storagePreferences.getMode()
                            ) {
                                MediaStorageMode.INTERNAL ->
                                    null

                                MediaStorageMode.EXTERNAL ->
                                    storagePreferences.getTreeUri()
                            }

                        migrationScope.launch {
                            mediaMoveBatchCoordinator
                                .start(
                                    destinationSnapshot
                                )
                        }
                    }
                ) {
                    Text(
                        "Move"
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showMoveConfirmation =
                            false
                    }
                ) {
                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }
}
