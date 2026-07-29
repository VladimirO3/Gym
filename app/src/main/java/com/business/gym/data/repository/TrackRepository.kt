package com.business.gym.data.repository

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.TrackDao
import com.business.gym.data.local.entity.TrackEntity
import com.business.gym.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import okhttp3.RequestBody

class TrackRepository(
    private val trackDao: TrackDao
) {
    private val apiService get() = NewsApiService.create()

    val allTracks: Flow<List<Track>> = trackDao.getAllTracks().map { entities ->
        entities.map { Track(id = it.id, name = it.name, url = it.url) }
    }

    suspend fun refreshTracks() {
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
                apiService.deleteLocalTrack("Bearer $token", id)
                android.util.Log.d("TrackRepository", "Track $id deleted from server")
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackRepository", "Server deletion failed for track $id", e)
            // Мы продолжаем выполнение, чтобы удалить из локальной БД в любом случае
            // или вы можете убрать это, если хотите удалять только при успехе сервера
        }
        
        // Удаляем из локальной БД, чтобы UI обновился мгновенно
        trackDao.deleteById(id)
    }

    suspend fun uploadTrack(
        token: String,
        name: RequestBody,
        media: MultipartBody.Part?
    ): okhttp3.ResponseBody {
        return apiService.postLocalTrack("Bearer $token", name, media)
    }
}
