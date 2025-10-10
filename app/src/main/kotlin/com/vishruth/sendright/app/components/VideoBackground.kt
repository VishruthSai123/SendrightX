/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vishruth.key1.app.components

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoBackground(
    rawVideoRes: Int,
    modifier: Modifier = Modifier,
    shouldLoop: Boolean = true,
    onVideoReady: () -> Unit = {},
    onProgressUpdate: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                // Optimize for fast startup
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                repeatMode = if (shouldLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = 0f // Mute the video for background playback
                
                // Set up media item from raw resource
                val uri = Uri.parse("android.resource://${context.packageName}/$rawVideoRes")
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                
                // Add listener for when video is ready and progress updates
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && !isVideoReady) {
                            isVideoReady = true
                            onVideoReady()
                        }
                    }
                })
                

                
                // Prepare and start playback
                prepare()
                playWhenReady = true
            }
    }

    // Handle progress updates
    LaunchedEffect(isVideoReady) {
        if (isVideoReady) {
            while (true) {
                kotlinx.coroutines.delay(100)
                val duration = exoPlayer.duration
                if (duration > 0) {
                    val progress = exoPlayer.currentPosition.toFloat() / duration.toFloat()
                    onProgressUpdate(progress)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Completely hide all controls and UI elements
                useController = false
                controllerAutoShow = false
                controllerHideOnTouch = true
                controllerShowTimeoutMs = 0
                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                
                // Set resize mode to fill entire screen
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                
                // Hide video surface until ready for smooth experience
                alpha = if (isVideoReady) 1f else 0f
                
                // Ensure video covers entire screen
                scaleX = 1.0f
                scaleY = 1.0f
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { playerView ->
            // Update alpha when video becomes ready
            playerView.alpha = if (isVideoReady) 1f else 0f
        }
    )
}