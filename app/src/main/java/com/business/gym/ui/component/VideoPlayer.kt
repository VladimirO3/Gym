package com.business.gym.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String, 
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer? = null,
    autoPlay: Boolean = false,
    muted: Boolean = false,
    looping: Boolean = false,
) {
    if (LocalInspectionMode.current || videoUrl.isBlank()) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(if (videoUrl.isBlank()) "No Video URL" else "Video Player Placeholder", color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val internalPlayer = remember {
        if (exoPlayer == null) ExoPlayer.Builder(context).build().apply {
            repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            playWhenReady = autoPlay
        } else null
    }
    val activePlayer = exoPlayer ?: internalPlayer!!
    
    var isFullMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Listener для отслеживания загрузки
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            activePlayer.removeListener(listener)
        }
    }

    LaunchedEffect(videoUrl) {
        if (activePlayer.currentMediaItem?.localConfiguration?.uri.toString() != videoUrl) {
            activePlayer.setMediaItem(MediaItem.fromUri(videoUrl))
            activePlayer.prepare()
        }
    }
    
    LaunchedEffect(isFullMode, muted, looping, autoPlay) {
        activePlayer.repeatMode = if (isFullMode) Player.REPEAT_MODE_OFF else (if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
        activePlayer.volume = if (isFullMode) 1f else (if (muted) 0f else 1f)
        if (isFullMode) activePlayer.play()
    }
    
    Box(modifier = modifier.background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = activePlayer
                    useController = isFullMode || !autoPlay
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // Используем свой индикатор
                }
            },
            update = {
                it.player = activePlayer
                it.useController = isFullMode || !autoPlay
            },
            modifier = Modifier.matchParentSize()
        )

        // Индикатор загрузки
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                color = Color.Red
            )
        }

        if (!isFullMode && autoPlay && muted) {
            IconButton(
                onClick = { isFullMode = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Enable Sound",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (exoPlayer == null) {
        DisposableEffect(internalPlayer) {
            onDispose {
                internalPlayer?.release()
            }
        }
    }
}
