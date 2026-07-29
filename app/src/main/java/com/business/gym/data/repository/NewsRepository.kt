package com.business.gym.data.repository

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.NewsDao
import com.business.gym.data.local.entity.NewsEntity
import com.business.gym.data.api.LocalNews
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import okhttp3.RequestBody

class NewsRepository(
    private val newsDao: NewsDao
) {
    private val apiService get() = NewsApiService.create()

    val allNews: Flow<List<LocalNews>> = newsDao.getAllNews().map { entities ->
        entities.map { LocalNews(id = it.id, title = it.title, content = it.content, url = it.url, type = it.type) }
    }

    suspend fun refreshNews(token: String?) {
        try {
            val authHeader = if (token.isNullOrBlank()) "" else "Bearer $token"
            val news = apiService.getLocalNews(authHeader)
            val entities = news.map { 
                NewsEntity(id = it.id, title = it.title, content = it.content, url = it.url, type = it.type)
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
        media: MultipartBody.Part?
    ): okhttp3.ResponseBody {
        val authHeader = "Bearer $token"
        return apiService.postLocalNews(authHeader, title, content, type, media)
    }
}
