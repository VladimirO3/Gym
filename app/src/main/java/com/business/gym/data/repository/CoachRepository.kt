package com.business.gym.data.repository

import android.content.Context
import android.util.Log
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.CoachDao
import com.business.gym.data.local.entity.CoachEntity
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class CoachRepository(
    private val coachDao: CoachDao,
    private val context: Context
) {
    private val apiService get() = NewsApiService.create(context)

    val allCoaches: Flow<List<CoachEntity>> = coachDao.getAllCoaches()

    suspend fun refreshCoaches(): Boolean {
        return try {
            Log.d("CoachRepository", "Refreshing coaches from API...")
            val response = apiService.getCoaches()
            Log.d("CoachRepository", "Received ${response.size} coaches from server")
            val entities = response.map {
                CoachEntity(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    imageUrl = it.imageUrl
                )
            }
            coachDao.deleteAllCoaches()
            coachDao.insertCoaches(entities)
            Log.d("CoachRepository", "Saved ${entities.size} coaches to Room")
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "FAILED to refresh coaches: ${e.message}", e)
            false
        }
    }

    suspend fun addCoach(name: String, description: String, imagePart: MultipartBody.Part?): Boolean {
        return try {
            Log.d("CoachRepository", "Attempting to add coach: $name. Has image: ${imagePart != null}")
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
            val response = apiService.addCoach(nameBody, descBody, imagePart)
            Log.d("CoachRepository", "Add coach success. Response: ${response.string()}")
            refreshCoaches()
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "FAILED to add coach: $name", e)
            if (e is retrofit2.HttpException) {
                Log.e("CoachRepository", "HTTP Error ${e.code()}: ${e.response()?.errorBody()?.string()}")
            }
            false
        }
    }

    suspend fun updateCoach(id: String, name: String, description: String, imagePart: MultipartBody.Part?): Boolean {
        return try {
            Log.d("CoachRepository", "Attempting to update coach ID=$id to name: $name")
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.updateCoach(id, nameBody, descBody, imagePart)
            Log.d("CoachRepository", "Update coach success for ID: $id")
            refreshCoaches()
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "FAILED to update coach ID: $id", e)
            false
        }
    }

    suspend fun deleteCoach(id: String): Boolean {
        return try {
            apiService.deleteCoach(id)
            refreshCoaches()
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "Failed to delete coach", e)
            false
        }
    }
}
