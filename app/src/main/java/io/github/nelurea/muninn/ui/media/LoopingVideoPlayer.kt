package io.github.nelurea.muninn.ui.media

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
        muted
    ) {
        player.volume =
            if (
                muted
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

    DisposableEffect(
        player
    ) {
        onDispose {
            player.release()
        }
    }

    ContentFrame(
        player =
            player,

        modifier =
            modifier,

        surfaceType =
            SURFACE_TYPE_TEXTURE_VIEW,

        contentScale =
            ContentScale.Fit,

        keepContentOnReset =
            true,

        shutter = {}
    )
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
