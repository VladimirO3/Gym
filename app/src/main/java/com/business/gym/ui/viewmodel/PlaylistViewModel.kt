package com.business.gym.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.LocalTrack
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.model.Track
import com.business.gym.data.repository.TrackRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * ViewModel для управления музыкой и плейлистами.
 * Обеспечивает воспроизведение из локального VPS сервера с поддержкой оффлайн режима через Room.
 */
class PlaylistViewModel(
    application: Application,
    private val repository: TrackRepository
) : AndroidViewModel(application) {
    
    // Треки с VPS (кэшированные в Room)
    private val _localTracks = mutableStateOf(listOf<LocalTrack>())
    val localTracks: State<List<LocalTrack>> = _localTracks

    // Состояние загрузки файла на сервер
    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        // Подписка на локальные треки (кэш Room)
        viewModelScope.launch {
            repository.allTracks.collect { tracks ->
                _localTracks.value = tracks.map { 
                    LocalTrack(id = it.id.toIntOrNull() ?: 0, name = it.name, url = it.url) 
                }
            }
        }
        
        // Фоновое обновление при запуске
        val sharedPref = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("user_session_token", null)
        fetchLocalTracks(token)
    }

    /**
     * Загружает список музыки с VPS и обновляет Room.
     */
    fun fetchLocalTracks(token: String?) {
        viewModelScope.launch {
            repository.refreshTracks(token)
        }
    }

    /**
     * Загрузка музыкального файла на VPS сервер.
     */
    fun uploadTrackToLocalServer(context: Context, uri: Uri, token: String?) {
        Log.d("PlaylistViewModel", "Starting uploadTrackToLocalServer")
        if (token == null) {
            Toast.makeText(context, "Ошибка: Авторизуйтесь для загрузки", Toast.LENGTH_LONG).show()
            return
        }

        _isUploading.value = true
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri) ?: "Track_${UUID.randomUUID()}"
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "audio/mpeg"
                
                val inputStream = contentResolver.openInputStream(uri)
                val filePart = if (inputStream != null) {
                    val bytes = inputStream.readBytes()
                    val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", fileName, requestFile)
                    inputStream.close()
                    part
                } else null

                if (filePart == null) throw Exception("Could not read file")

                repository.uploadTrack(token, fileName, filePart)
                
                Toast.makeText(context, "Трек добавлен!", Toast.LENGTH_SHORT).show()
                repository.refreshTracks(token)
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Local upload failed", e)
                Toast.makeText(context, "Ошибка сервера: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/')
    }

    /**
     * Удаление трека с VPS и из Room.
     */
    fun deleteLocalTrack(id: String, token: String?) {
        viewModelScope.launch {
            repository.deleteTrack(id, token)
        }
    }

    // Для совместимости с UI
    val tracks: State<List<Track>> = mutableStateOf(emptyList())

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaylistViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = TrackRepository(database.trackDao(), application)
                @Suppress("UNCHECKED_CAST")
                return PlaylistViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
