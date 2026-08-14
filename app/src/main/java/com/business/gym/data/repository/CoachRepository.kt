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
            val response = apiService.getCoaches()
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
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "Failed to refresh coaches", e)
            false
        }
    }

    suspend fun addCoach(name: String, description: String, imagePart: MultipartBody.Part?): Boolean {
        return try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.addCoach(nameBody, descBody, imagePart)
            refreshCoaches()
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "Failed to add coach", e)
            false
        }
    }

    suspend fun updateCoach(id: String, name: String, description: String, imagePart: MultipartBody.Part?): Boolean {
        return try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.updateCoach(id, nameBody, descBody, imagePart)
            refreshCoaches()
            true
        } catch (e: Exception) {
            Log.e("CoachRepository", "Failed to update coach", e)
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
