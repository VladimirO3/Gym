package com.business.gym.ui.component

import android.util.Log
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.business.gym.data.api.NewsApiService

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
    val context = LocalContext.current
    val fullUrl = remember(videoUrl) { NewsApiService.getFullUrl(context, videoUrl) }

    if (LocalInspectionMode.current || fullUrl.isBlank()) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(if (fullUrl.isBlank()) "No Video URL" else "Video Player Placeholder", color = Color.White)
        }
        return
    }
    
    // Получаем токен из SharedPreferences
    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
    val token = sharedPref.getString("user_session_token", null)

    val internalPlayer = remember(token) {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // min buffer 15s
                50000, // max buffer 50s
                2500,  // buffer for playback 2.5s
                5000   // buffer for playback after rebuffer 5s
            )
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(NewsApiService.getMediaSourceFactory(context))
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                volume = if (muted) 0f else 1f
                playWhenReady = autoPlay
            }
    }
    
    // Используем переданный плеер или созданный внутри
    val activePlayer = exoPlayer ?: internalPlayer
    
    var isFullMode by remember { mutableStateOf(value = false) }
    var isLoading by remember { mutableStateOf(value = true) }
    var errorMessage by remember { mutableStateOf<String?>(value = null) }

    // Listener для отслеживания состояния
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE)
                Log.d("VideoPlayer", "State changed: $state for $videoUrl")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMessage = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Ошибка сети: Сервер недоступен"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Тайм-аут: Сервер не отвечает"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Файл не найден на сервере"
                    else -> "Ошибка воспроизведения: ${error.localizedMessage}"
                }
                Log.e("VideoPlayer", "Player error: ${error.errorCodeName} (${error.errorCode})", error)
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            activePlayer.removeListener(listener)
        }
    }

    // Загрузка контента
    LaunchedEffect(fullUrl, activePlayer) {
        Log.d("VideoPlayer", "Loading URL: $fullUrl")
        errorMessage = null
        try {
            val mediaItem = MediaItem.fromUri(fullUrl)
            activePlayer.setMediaItem(mediaItem)
            activePlayer.prepare()
            if (autoPlay) activePlayer.play()
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
    }
    
    // Управление параметрами
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
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = {
                it.player = activePlayer
                it.useController = isFullMode || !autoPlay
            },
            modifier = Modifier.matchParentSize()
        )

        // Индикатор загрузки
        if (isLoading && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                color = Color.Red
            )
        }

        // Сообщение об ошибке
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodySmall
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

    // Очистка только если мы создали плеер сами
    if (exoPlayer == null) {
        DisposableEffect(Unit) {
            onDispose {
                internalPlayer.release()
            }
        }
    }
}
