package com.business.gym.data.repository

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.TrackDao
import com.business.gym.data.local.entity.TrackEntity
import com.business.gym.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackRepository(
    private val apiService: NewsApiService,
    private val trackDao: TrackDao
) {
    val allTracks: Flow<List<Track>> = trackDao.getAllTracks().map { entities ->
        entities.map { Track(id = it.id, name = it.name, url = it.url) }
    }

    suspend fun refreshTracks() {
        try {
            val localTracks = apiService.getLocalTracks()
            val entities = localTracks.map { 
                TrackEntity(id = it.id.toString(), name = it.name, url = it.url) 
            }
            trackDao.deleteAll()
            trackDao.insertAll(entities)
        } catch (e: Exception) {
            // Log error or handle it
        }
    }
}
