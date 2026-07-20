package com.business.gym.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun VideoPlayer(
    videoUrl: String, 
    exoPlayer: ExoPlayer? = null,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Video Player Placeholder", color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val internalPlayer = remember {
        if (exoPlayer == null) ExoPlayer.Builder(context).build() else null
    }
    val activePlayer = exoPlayer ?: internalPlayer!!
    
    LaunchedEffect(videoUrl) {
        activePlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        activePlayer.prepare()
        activePlayer.playWhenReady = false // Don't auto-play by default
    }
    
    androidx.compose.ui.viewinterop.AndroidView(
        factory = {
            androidx.media3.ui.PlayerView(context).apply {
                player = activePlayer
                useController = true
            }
        },
        update = {
            it.player = activePlayer
        },
        modifier = modifier
    )

    if (exoPlayer == null) {
        DisposableEffect(internalPlayer) {
            onDispose {
                internalPlayer?.release()
            }
        }
    } else {
        // If external player, just ensure it stops when this composable leaves
        DisposableEffect(activePlayer) {
            onDispose {
                activePlayer.stop()
            }
        }
    }
}
