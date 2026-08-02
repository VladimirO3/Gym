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
import androidx.media3.exoplayer.ExoPlayer
import com.business.gym.R
import com.business.gym.data.model.Track
import com.business.gym.ui.component.TrackItem
import com.business.gym.ui.viewmodel.PlaylistViewModel
import com.business.gym.ui.viewmodel.AuthViewModel

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun PlaylistScreen(
    exoPlayer: ExoPlayer,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: PlaylistViewModel = viewModel(
        factory = PlaylistViewModel.Factory(application)
    )

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val columns = if (isWideScreen) 2 else 1
    
    val tracks by viewModel.tracks
    val localTracks by viewModel.localTracks
    val isUploading by viewModel.isUploading
    val jwtToken by authViewModel.jwtToken
    
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // Лаунчер для выбора аудио (теперь грузим на свой сервер)
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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    val currentUserEmail by authViewModel.currentUserEmail
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }
    val settingsViewModel: com.business.gym.ui.viewmodel.SettingsViewModel = viewModel(factory = com.business.gym.ui.viewmodel.SettingsViewModel.Factory(application))
    val serverIp by settingsViewModel.serverIp

    fun getFullUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        if (rawUrl.startsWith("http")) return rawUrl
        
        val cleanIp = serverIp.removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val base = "http://$cleanIp"
        
        val cleanRaw = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
        return base + cleanRaw
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
                    // Кнопка загрузки на СВОЙ сервер
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

        if (tracks.isEmpty() && localTracks.isEmpty()) {
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
                // Сначала треки из Firebase
                items(tracks) { track ->
                    val isThisTrackSelected = currentTrack?.id == track.id
                    TrackItem(
                        track = track,
                        isSelected = isThisTrackSelected,
                        isPlaying = isPlaying,
                        isAdmin = isAdmin,
                        onDelete = { viewModel.deleteTrack(track) },
                        onPlayPause = {
                            if (!isThisTrackSelected) {
                                currentTrack = track
                                exoPlayer.setMediaItem(MediaItem.fromUri(track.url))
                                exoPlayer.prepare()
                                exoPlayer.play()
                            } else {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        },
                        onStop = {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            currentTrack = null
                        }
                    )
                }

                // Затем треки с вашего сервера
                if (localTracks.isNotEmpty()) {
                    item(span = { GridItemSpan(columns) }) {
                        Text(
                            "Плейлист с вашего сервера:", 
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Red,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(localTracks) { localTrack ->
                        val fullUrl = getFullUrl(localTrack.url)
                        val isThisTrackSelected = currentTrack?.url == fullUrl
                        TrackItem(
                            track = Track(id = localTrack.id.toString(), url = fullUrl, name = localTrack.name),
                            isSelected = isThisTrackSelected,
                            isPlaying = isPlaying,
                            isAdmin = isAdmin,
                            onDelete = { viewModel.deleteLocalTrack(localTrack.id.toString(), jwtToken) },
                            onPlayPause = {
                                if (!isThisTrackSelected) {
                                    currentTrack = Track(id = localTrack.id.toString(), url = fullUrl, name = localTrack.name)
                                    exoPlayer.setMediaItem(MediaItem.fromUri(fullUrl))
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                } else {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            },
                            onStop = {
                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                                currentTrack = null
                            }
                        )
                    }
                }
            }
        }

        if (currentTrack != null) {
            Card(
                modifier = topRowModifier.padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentTrack?.name ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            currentTrack = null
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Red)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(
                            onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Red,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        }
                    }
                }
            }
        }
    }
}
