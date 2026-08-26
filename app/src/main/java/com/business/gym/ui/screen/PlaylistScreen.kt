package com.business.gym.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.business.gym.R
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.model.Track
import com.business.gym.ui.component.TrackItem
import com.business.gym.ui.viewmodel.PlaylistViewModel
import com.business.gym.ui.viewmodel.AuthViewModel

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay

@Composable
fun PlaylistScreen(
    player: Player,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: PlaylistViewModel = viewModel(
        factory = PlaylistViewModel.Factory(application)
    )
    val settingsViewModel: com.business.gym.ui.viewmodel.SettingsViewModel = viewModel(
        factory = com.business.gym.ui.viewmodel.SettingsViewModel.Factory(application)
    )

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val columns = if (isWideScreen) 2 else 1
    
    val localTracks by viewModel.localTracks
    val isUploading by viewModel.isUploading
    val jwtToken by authViewModel.jwtToken

    LaunchedEffect(jwtToken) {
        viewModel.fetchLocalTracks(jwtToken)
    }
    
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var currentTrackIndex by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Плеер: состояние прогресса
    var currentPosition by remember { mutableLongStateOf(0L) }
    var trackDuration by remember { mutableLongStateOf(0L) }
    var isShuffleMode by remember { mutableStateOf(player.shuffleModeEnabled) }
    
    // Вспомогательное состояние для плавного скролла ползунка пользователем
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableLongStateOf(0L) }

    // Обновление прогресса воспроизведения
    LaunchedEffect(isPlaying) {
        while (true) {
            if (isPlaying && !isDraggingSlider) {
                currentPosition = player.currentPosition
                trackDuration = player.duration.coerceAtLeast(0L)
                // Дополнительно синхронизируем индекс, если он сменился без транзишена (редко, но бывает)
                if (currentTrackIndex != player.currentMediaItemIndex) {
                    currentTrackIndex = player.currentMediaItemIndex
                }
            }
            delay(500)
        }
    }

    // Helper: форматирование времени
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    // Функция для запуска всего плейлиста. 
    // Загружает все доступные треки в очередь плеера для возможности переключения (Skip Next/Prev)
    // и устанавливает режим цикличного воспроизведения.
    fun playPlaylist(startIndex: Int, allTracks: List<Track>) {
        player.stop()
        player.clearMediaItems()
        
        val mediaItems = allTracks.map { track ->
            MediaItem.Builder()
                .setUri(track.url)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track.name) // Устанавливаем заголовок для корректного отображения в уведомлении и UI
                        .build()
                )
                .build()
        }
        player.addMediaItems(mediaItems)
        player.repeatMode = Player.REPEAT_MODE_ALL // Активируем бесконечный цикл плейлиста
        player.shuffleModeEnabled = isShuffleMode // Применяем текущий режим перемешивания
        player.seekTo(startIndex, 0L)
        player.prepare()
        player.play()
        
        currentTrack = allTracks[startIndex]
        currentTrackIndex = startIndex
    }

    // Лаунчер для выбора аудио
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: ""
            if (mimeType.startsWith("audio/")) {
                viewModel.uploadTrackToLocalServer(context, it, jwtToken)
            } else {
                Toast.makeText(context, "Это не музыкальный файл!", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            launcher.launch("audio/*")
        } else {
            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndLaunch() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    // Synchronize currentTrack and isPlaying with player state
    LaunchedEffect(player, localTracks) {
        isPlaying = player.isPlaying
        currentTrackIndex = player.currentMediaItemIndex
        val mediaItem = player.currentMediaItem
        if (mediaItem != null) {
            val url = mediaItem.localConfiguration?.uri?.toString() ?: ""
            val allTracks = localTracks.map { 
                val fullUrl = NewsApiService.getFullUrl(context, it.url)
                Track(id = it.id.toString(), url = fullUrl, name = it.name)
            }
            currentTrack = allTracks.find { it.url == url } 
                ?: Track(id = "remote", url = url, name = mediaItem.mediaMetadata.title?.toString() ?: "Неизвестный трек")
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrackIndex = player.currentMediaItemIndex
                if (mediaItem != null) {
                    val url = mediaItem.localConfiguration?.uri?.toString() ?: ""
                    val allTracksList = localTracks.map {
                        val fullUrl = NewsApiService.getFullUrl(context, it.url)
                        Track(id = it.id.toString(), url = fullUrl, name = it.name)
                    }
                    // Синхронизируем currentTrack с тем, что реально играет в плеере для подсветки
                    currentTrack = allTracksList.find { it.url == url }
                        ?: Track(id = "sync", url = url, name = mediaItem.mediaMetadata.title?.toString() ?: "Неизвестный трек")
                } else {
                    currentTrack = null
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                isShuffleMode = shuffleModeEnabled
                android.util.Log.d("PlaylistScreen", "Shuffle mode changed: $shuffleModeEnabled")
            }
        }
        player.addListener(listener)
        // Синхронизируем начальное состояние
        isShuffleMode = player.shuffleModeEnabled
        currentTrackIndex = player.currentMediaItemIndex
        onDispose { player.removeListener(listener) }
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val topRowModifier = if (isWideScreen) Modifier.width(800.dp) else Modifier.fillMaxWidth()
        
        Row(
            modifier = topRowModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.playlist_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (isAdmin) {
                Row {
                    IconButton(
                        onClick = { checkAndLaunch() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF8B0000))
                    ) {
                        if (isUploading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Icon(Icons.Default.CloudUpload, contentDescription = "Загрузить на сервер", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (localTracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allTracksList = localTracks.map { 
                    val fullUrl = NewsApiService.getFullUrl(context, it.url)
                    Track(id = it.id.toString(), url = fullUrl, name = it.name)
                }

                items(localTracks.indices.toList()) { index ->
                    val localTrack = localTracks[index]
                    val fullUrl = NewsApiService.getFullUrl(context, localTrack.url)
                    val absoluteIndex = index
                    val isThisTrackSelected = currentTrackIndex == absoluteIndex
                    TrackItem(
                        track = Track(id = localTrack.id.toString(), url = fullUrl, name = localTrack.name),
                        isSelected = isThisTrackSelected,
                        isPlaying = isPlaying,
                        isAdmin = isAdmin,
                        onDelete = { viewModel.deleteLocalTrack(localTrack.id.toString(), jwtToken) },
                        onPlayPause = {
                            if (!isThisTrackSelected) {
                                playPlaylist(absoluteIndex, allTracksList)
                            } else {
                                if (isPlaying) player.pause() else player.play()
                            }
                        },
                        onStop = {
                            player.stop()
                            player.clearMediaItems()
                            currentTrack = null
                            currentTrackIndex = -1
                        }
                    )
                }
            }
        }

        if (currentTrack != null) {
            Card(
                modifier = topRowModifier.padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentTrack?.name ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Скролл (Slider) и Время
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Slider(
                            value = (if (isDraggingSlider) sliderPosition else currentPosition).toFloat(),
                            onValueChange = { 
                                isDraggingSlider = true
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                player.seekTo(sliderPosition)
                                currentPosition = sliderPosition
                                isDraggingSlider = false
                            },
                            valueRange = 0f..(if (trackDuration > 0) trackDuration.toFloat() else 1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Red,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(if (isDraggingSlider) sliderPosition else currentPosition), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(formatTime(trackDuration), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка перемешивания
                        IconButton(onClick = { 
                            val nextShuffleMode = !isShuffleMode
                            player.shuffleModeEnabled = nextShuffleMode
                            isShuffleMode = nextShuffleMode
                        }) {
                            Icon(
                                Icons.Default.Shuffle, 
                                contentDescription = "Shuffle", 
                                tint = if (isShuffleMode) Color.Red else Color.Gray
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { player.seekToPrevious() }) {
                                Icon(Icons.Default.SkipPrevious, null, tint = Color.White)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            IconButton(
                                onClick = { if (isPlaying) player.pause() else player.play() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Red,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                                    null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(onClick = { player.seekToNext() }) {
                                Icon(Icons.Default.SkipNext, null, tint = Color.White)
                            }
                        }

                        IconButton(onClick = {
                            player.stop()
                            player.clearMediaItems()
                            currentTrack = null
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
