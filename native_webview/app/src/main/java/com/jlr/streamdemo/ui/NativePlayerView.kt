package com.jlr.streamdemo.ui

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.jlr.streamdemo.R
import com.jlr.streamdemo.StreamConfig

@Composable
fun NativePlayerView(
    streamUrl: String,
    quality:   StreamConfig.Quality,
    modifier:  Modifier = Modifier,
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Track selector remembered separately so LaunchedEffect(quality) can update
    // it while the player keeps running — no restart, no seek.
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            // Safe initial cap: prevents the goldfish decoder from being asked to
            // handle 60fps content before LaunchedEffect fires.
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(480, 270)
                    .setMaxVideoBitrate(600_000)
            )
        }
    }

    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .also { p ->
                p.setMediaItem(MediaItem.fromUri(streamUrl))
                p.prepare()
                p.playWhenReady = true
            }
    }

    // Live quality switch — ExoPlayer applies at the next segment boundary (~2–6 s).
    LaunchedEffect(quality) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(quality.maxWidth, quality.maxHeight)
                .setMaxVideoBitrate(quality.maxBitrate)
        )
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.play()
                Lifecycle.Event.ON_PAUSE  -> player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            // surface_type="texture_view" composites correctly inside Compose's GL tree.
            (LayoutInflater.from(ctx).inflate(R.layout.native_player_view, null) as PlayerView)
                .apply { this.player = player }
        },
        modifier = modifier,
    )
}
