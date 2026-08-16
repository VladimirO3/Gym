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
                createdAt = it.createdAt,
                userReaction = it.userReaction,
                reactions = mapOf(
                    "fire" to it.fireCount,
                    "heart" to it.heartCount,
                    "muscle" to it.muscleCount,
                    "thumb" to it.thumbCount,
                    "wow" to it.wowCount
                )
            ) 
        }
    }

    suspend fun refreshNews(token: String? = null) {
        val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
        val savedToken = sharedPref.getString("user_session_token", null)
        val effectiveToken = if (!token.isNullOrBlank()) token else savedToken

        if (effectiveToken == "guest_token") return

        try {
            val news = apiService.getLocalNews()
            android.util.Log.d("NewsRepository", "Refreshing news, count: ${news.size}")
            
            val entities = news.map { 
                NewsEntity(
                    id = it.id, 
                    title = it.title, 
                    content = it.content, 
                    mediaUrl = it.mediaUrl, 
                    mediaType = it.mediaType,
                    createdAt = it.createdAt,
                    userReaction = it.userReaction,
                    fireCount = it.reactions["fire"] ?: 0,
                    heartCount = it.reactions["heart"] ?: 0,
                    muscleCount = it.reactions["muscle"] ?: 0,
                    thumbCount = it.reactions["thumb"] ?: 0,
                    wowCount = it.reactions["wow"] ?: 0
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
        val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
        val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
        val typeBody = type.toRequestBody("text/plain".toMediaTypeOrNull())
        
        return apiService.postLocalNews(
            titleBody, 
            contentBody, 
            typeBody, 
            filePart
        )
    }

    suspend fun postReaction(token: String, id: String, type: String) {
        apiService.postReaction(id, type)
    }
}
