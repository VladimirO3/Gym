package com.business.gym.data.repository

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.NewsDao
import com.business.gym.data.local.entity.NewsEntity
import com.business.gym.data.api.LocalNews
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class NewsRepository(
    private val newsDao: NewsDao,
    private val context: android.content.Context
) {
    private val apiService get() = NewsApiService.create(context)

    val allNews: Flow<List<LocalNews>> = newsDao.getAllNews().map { entities ->
        entities.map { 
            LocalNews(
                id = it.id, 
                title = it.title, 
                content = it.content, 
                mediaUrl = it.mediaUrl, 
                mediaType = it.mediaType,
                createdAt = it.createdAt
            ) 
        }
    }

    suspend fun refreshNews(token: String?) {
        try {
            val news = apiService.getLocalNews()
            val entities = news.map { 
                NewsEntity(
                    id = it.id, 
                    title = it.title, 
                    content = it.content, 
                    mediaUrl = it.mediaUrl, 
                    mediaType = it.mediaType,
                    createdAt = it.createdAt
                )
            }
            newsDao.deleteAll()
            newsDao.insertAll(entities)
        } catch (e: Exception) {
            android.util.Log.e("NewsRepository", "Failed to refresh news", e)
        }
    }

    suspend fun uploadNews(
        token: String,
        title: String,
        content: String,
        type: String,
        filePart: MultipartBody.Part? = null
    ): okhttp3.ResponseBody {
        val authHeader = "Bearer $token"
        val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
        val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
        val typeBody = type.toRequestBody("text/plain".toMediaTypeOrNull())
        
        return apiService.postLocalNews(
            authHeader, 
            titleBody, 
            contentBody, 
            typeBody, 
            filePart
        )
    }
}
