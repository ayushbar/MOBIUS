package com.jlr.adaptive.ui

import android.view.LayoutInflater
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.jlr.adaptive.R
import com.jlr.adaptive.StreamConfig

@Composable
fun PlayerSection(
    streamUrl:           String,
    quality:             StreamConfig.Quality,
    onResolutionChanged: (Int, Int) -> Unit = { _, _ -> },
    modifier:            Modifier = Modifier,
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var actualWidth  by remember { mutableIntStateOf(0) }
    var actualHeight by remember { mutableIntStateOf(0) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoBitrate(quality.maxBitrate))
        }
    }

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_000, 3_000, 500, 1_000)
            .build()
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .also { p ->
                p.setMediaItem(MediaItem.fromUri(streamUrl))
                p.prepare()
                p.playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                actualWidth  = videoSize.width
                actualHeight = videoSize.height
                onResolutionChanged(videoSize.width, videoSize.height)
            }

            // When the manifest loads, disable any secondary video track groups.
            override fun onTracksChanged(tracks: Tracks) {
                val videoIdx = (0 until tracks.groups.size)
                    .filter { tracks.groups[it].type == C.TRACK_TYPE_VIDEO }
                if (videoIdx.size <= 1) return

                // Keep the group with the most format variants (the ABR ladder)
                val primaryIdx = videoIdx.maxByOrNull { tracks.groups[it].length } ?: return
                val params = trackSelector.buildUponParameters()
                videoIdx.filter { it != primaryIdx }.forEach { i ->
                    params.addOverride(
                        TrackSelectionOverride(tracks.groups[i].mediaTrackGroup, listOf<Int>())
                    )
                }
                trackSelector.setParameters(params)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Apply bitrate cap whenever middleware changes quality.
    // buildUponParameters preserves the secondary-group overrides set above.
    LaunchedEffect(quality) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters().setMaxVideoBitrate(quality.maxBitrate)
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

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView)
                    .apply { this.player = player }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
