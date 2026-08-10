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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class PlaylistViewModel(
    application: Application,
    private val repository: TrackRepository
) : AndroidViewModel(application) {
    private val database = FirebaseDatabase.getInstance().getReference("playlist")
    private val storage = FirebaseStorage.getInstance().getReference("music")
    private val localApiService get() = NewsApiService.create(getApplication())

    private val _tracks = mutableStateOf(listOf<Track>())
    val tracks: State<List<Track>> = _tracks

    private val _localTracks = mutableStateOf(listOf<LocalTrack>())
    val localTracks: State<List<LocalTrack>> = _localTracks

    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    // Подписка на локальные треки из SQLite
    init {
        fetchTracks()
        
        viewModelScope.launch {
            repository.allTracks.collect { tracks ->
                // Мы можем объединять или использовать отдельно. 
                // В данном случае просто обновляем _localTracks для совместимости
                _localTracks.value = tracks.map { 
                    LocalTrack(id = it.id.toIntOrNull() ?: 0, name = it.name, url = it.url) 
                }
            }
        }
    }

    fun fetchLocalTracks(token: String?) {
        viewModelScope.launch {
            repository.refreshTracks(token)
        }
    }

    fun uploadTrackToLocalServer(context: Context, uri: Uri, token: String?) {
        Log.d("PlaylistViewModel", "Starting uploadTrackToLocalServer. HasToken: ${token != null}")
        if (token == null) {
            Log.e("PlaylistViewModel", "Upload failed: JWT Token is null")
            Toast.makeText(context, "Ошибка: Токен сервера не найден. Перезайдите или проверьте настройки.", Toast.LENGTH_LONG).show()
            return
        }

        _isUploading.value = true
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri) ?: "Track_${UUID.randomUUID()}"
                Log.d("PlaylistViewModel", "File name: $fileName")
                
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "audio/mpeg"
                
                val inputStream = contentResolver.openInputStream(uri)
                val filePart = if (inputStream != null) {
                    val bytes = inputStream.readBytes()
                    val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", fileName, requestFile)
                    inputStream.close()
                    part
                } else {
                    null
                }

                if (filePart == null) {
                    throw Exception("Could not read file from Uri")
                }

                Log.d("PlaylistViewModel", "Calling repository.uploadTrack...")
                repository.uploadTrack(
                    token = token,
                    name = fileName,
                    filePart = filePart
                )
                Log.d("PlaylistViewModel", "Server response received")
                
                kotlinx.coroutines.delay(500)
                
                Toast.makeText(context, "Трек добавлен!", Toast.LENGTH_SHORT).show()
                repository.refreshTracks(token)
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "CRITICAL: Local upload failed", e)
                Toast.makeText(context, "Ошибка сервера: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun fetchTracks() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val trackList = snapshot.children.mapNotNull { it.getValue(Track::class.java) }
                _tracks.value = trackList
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("PlaylistViewModel", "Database error: ${error.message}")
            }
        })
    }

    fun uploadTracks(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        _isUploading.value = true

        uris.forEach { uri ->
            val fileName = getFileName(context, uri) ?: "Track_${UUID.randomUUID()}"
            val fileRef = storage.child("${UUID.randomUUID()}_$fileName")

            fileRef.putFile(uri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    fileRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val trackId = database.push().key ?: UUID.randomUUID().toString()
                        val newTrack = Track(trackId, task.result.toString(), fileName)
                        database.child(trackId).setValue(newTrack)
                    }
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

    fun deleteTrack(track: Track) {
        database.child(track.id).removeValue()
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(track.url).delete()
        } catch (e: Exception) {
            Log.e("PlaylistViewModel", "Error deleting file", e)
        }
    }

    fun deleteLocalTrack(id: String, token: String?) {
        viewModelScope.launch {
            repository.deleteTrack(id, token)
        }
    }

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
