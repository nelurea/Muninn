package io.github.nelurea.muninn.ui.media

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

@Composable
fun LoopingVideoPlayer(
    uri: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    muted: Boolean = true
) {
    val context =
        LocalContext.current

    val lifecycleOwner =
        context.findLifecycleOwner()

    var isMuted by remember(
        uri,
        muted
    ) {
        mutableStateOf(
            muted
        )
    }

    var hasAudioTrack by remember(
        uri
    ) {
        mutableStateOf(
            false
        )
    }

    var showUnsafeAudioDialog by remember(
        uri
    ) {
        mutableStateOf(
            false
        )
    }

    val player =
        remember(
            uri
        ) {
            ExoPlayer
                .Builder(
                    context.applicationContext
                )
                .build()
                .apply {
                    setMediaItem(
                        MediaItem.fromUri(
                            uri
                        )
                    )

                    repeatMode =
                        Player.REPEAT_MODE_ONE

                    volume =
                        if (
                            muted
                        ) {
                            0f
                        } else {
                            1f
                        }

                    prepare()
                }
        }

    LaunchedEffect(
        player,
        active,
        isMuted
    ) {
        player.volume =
            if (
                isMuted
            ) {
                0f
            } else {
                1f
            }

        val lifecycleStarted =
            lifecycleOwner
                ?.lifecycle
                ?.currentState
                ?.isAtLeast(
                    Lifecycle.State.STARTED
                )
                ?: true

        player.playWhenReady =
            active &&
                    lifecycleStarted
    }

    DisposableEffect(
        player
    ) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(
                    tracks: Tracks
                ) {
                    hasAudioTrack =
                        tracks.groups.any {
                                group ->
                            group.type ==
                                    C.TRACK_TYPE_AUDIO
                        }
                }
            }

        player.addListener(
            listener
        )

        hasAudioTrack =
            player.currentTracks.groups.any {
                    group ->
                group.type ==
                        C.TRACK_TYPE_AUDIO
            }

        onDispose {
            player.removeListener(
                listener
            )
            player.release()
        }
    }

    DisposableEffect(
        lifecycleOwner,
        player,
        active
    ) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event ->
                when (
                    event
                ) {
                    Lifecycle.Event.ON_START -> {
                        player.playWhenReady =
                            active
                    }

                    Lifecycle.Event.ON_STOP -> {
                        player.playWhenReady =
                            false
                    }

                    else -> Unit
                }
            }

        lifecycleOwner
            ?.lifecycle
            ?.addObserver(
                observer
            )

        onDispose {
            lifecycleOwner
                ?.lifecycle
                ?.removeObserver(
                    observer
                )
        }
    }

    Box(
        modifier =
            modifier
    ) {
        ContentFrame(
            player =
                player,

            modifier =
                Modifier.fillMaxSize(),

            surfaceType =
                SURFACE_TYPE_TEXTURE_VIEW,

            contentScale =
                ContentScale.Fit,

            keepContentOnReset =
                true,

            shutter = {}
        )

        if (
            hasAudioTrack
        ) {
            Surface(
                modifier =
                    Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(
                            12.dp
                        ),
                shape =
                    MaterialTheme.shapes.extraLarge,
                color =
                    MaterialTheme
                        .colorScheme
                        .surface
                        .copy(
                            alpha = 0.72f
                        )
            ) {
                IconButton(
                    onClick = {
                        if (
                            !isMuted
                        ) {
                            isMuted =
                                true
                        } else if (
                            context.hasPrivateMediaAudioRoute()
                        ) {
                            isMuted =
                                false
                        } else {
                            showUnsafeAudioDialog =
                                true
                        }
                    }
                ) {
                    Icon(
                        imageVector =
                            if (
                                isMuted
                            ) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                        contentDescription =
                            if (
                                isMuted
                            ) {
                                "Unmute video"
                            } else {
                                "Mute video"
                            }
                    )
                }
            }
        }
    }

    if (
        showUnsafeAudioDialog
    ) {
        AlertDialog(
            onDismissRequest = {
                showUnsafeAudioDialog =
                    false
            },
            title = {
                Text(
                    "Audio output warning"
                )
            },
            text = {
                Text(
                    "Audio may play through a speaker."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsafeAudioDialog =
                            false

                        isMuted =
                            false
                    }
                ) {
                    Text(
                        "Play sound"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsafeAudioDialog =
                            false
                    }
                ) {
                    Text(
                        "Keep muted"
                    )
                }
            }
        )
    }
}

private tailrec fun Context
        .findLifecycleOwner(): LifecycleOwner? =
    when (
        this
    ) {
        is LifecycleOwner ->
            this

        is ContextWrapper ->
            baseContext
                .findLifecycleOwner()

        else ->
            null
    }

private fun Context.hasPrivateMediaAudioRoute(): Boolean {
    val audioManager =
        getSystemService(
            Context.AUDIO_SERVICE
        ) as? AudioManager
            ?: return false

    if (
        Build.VERSION.SDK_INT <
        Build.VERSION_CODES.TIRAMISU
    ) {
        return false
    }

    val mediaAttributes =
        AudioAttributes
            .Builder()
            .setUsage(
                AudioAttributes.USAGE_MEDIA
            )
            .setContentType(
                AudioAttributes.CONTENT_TYPE_MOVIE
            )
            .build()

    val routedDevices =
        audioManager
            .getAudioDevicesForAttributes(
                mediaAttributes
            )

    if (
        routedDevices.isEmpty()
    ) {
        return false
    }

    return routedDevices.all {
            device ->

        device.isPrivateAudioDevice()
    }
}

private fun AudioDeviceInfo.isPrivateAudioDevice(): Boolean =
    when (
        type
    ) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID ->
            true

        else ->
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {
                type ==
                    AudioDeviceInfo.TYPE_BLE_HEADSET
            } else {
                false
            }
    }
