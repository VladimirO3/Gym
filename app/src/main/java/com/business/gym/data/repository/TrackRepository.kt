package com.business.gym.data.repository

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.TrackDao
import com.business.gym.data.local.entity.TrackEntity
import com.business.gym.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class TrackRepository(
    private val trackDao: TrackDao,
    private val context: android.content.Context
) {
    private val apiService get() = NewsApiService.create(context)

    val allTracks: Flow<List<Track>> = trackDao.getAllTracks().map { entities ->
        entities.map { Track(id = it.id, name = it.name, url = it.url) }
    }

    suspend fun refreshTracks(token: String?) {
        if (token.isNullOrBlank() || token == "guest_token") return
        try {
            android.util.Log.d("TrackRepository", "Refreshing tracks from local server...")
            val localTracks = apiService.getLocalTracks()
            android.util.Log.d("TrackRepository", "Received ${localTracks.size} tracks from server")
            val entities = localTracks.map { 
                TrackEntity(id = it.id.toString(), name = it.name, url = it.url) 
            }
            // Используем транзакцию для атомарного обновления: удалить всё и вставить новое
            trackDao.updateData(entities)
        } catch (e: Exception) {
            android.util.Log.e("TrackRepository", "Failed to refresh tracks: ${e.message}", e)
        }
    }

    suspend fun deleteTrack(id: String, token: String?) {
        try {
            if (token != null) {
                // Выполняем запрос к API
                apiService.deleteLocalTrack(id)
                android.util.Log.d("TrackRepository", "Track $id deleted from server")
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackRepository", "Server deletion failed for track $id", e)
            // Мы продолжаем выполнение, чтобы удалить из локальной БД в любом случае
        }
        
        // Удаляем из локальной БД, чтобы UI обновился мгновенно
        trackDao.deleteById(id)
    }

    suspend fun uploadTrack(
        token: String,
        name: String,
        filePart: MultipartBody.Part
    ): okhttp3.ResponseBody {
        val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
        return apiService.postLocalTrack(nameBody, filePart)
    }
}
