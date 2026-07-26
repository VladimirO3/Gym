package com.business.gym.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
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

import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String, 
    exoPlayer: ExoPlayer? = null,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    muted: Boolean = false,
    looping: Boolean = false
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
    
    var isFullMode by remember { mutableStateOf(false) }

    LaunchedEffect(videoUrl, autoPlay, muted, looping, isFullMode) {
        if (!activePlayer.isPlaying || activePlayer.currentMediaItem?.localConfiguration?.uri.toString() != videoUrl) {
            activePlayer.setMediaItem(MediaItem.fromUri(videoUrl))
            activePlayer.prepare()
        }
        
        activePlayer.repeatMode = if (isFullMode) Player.REPEAT_MODE_OFF else (if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
        activePlayer.volume = if (isFullMode) 1f else (if (muted) 0f else 1f)
        activePlayer.playWhenReady = if (isFullMode) true else autoPlay
    }
    
    Box(modifier = modifier) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = {
                androidx.media3.ui.PlayerView(context).apply {
                    player = activePlayer
                    useController = isFullMode || !autoPlay
                    // Устанавливаем режим заполнения, чтобы контроллер был в самом низу
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = {
                it.player = activePlayer
                it.useController = isFullMode || !autoPlay
            },
            modifier = Modifier.matchParentSize()
        )

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
    } else {
        // If external player, just ensure it stops when this composable leaves
        DisposableEffect(activePlayer) {
            onDispose {
                activePlayer.stop()
            }
        }
    }
}
