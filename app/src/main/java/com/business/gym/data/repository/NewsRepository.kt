package com.business.gym.data.repository

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.NewsDao
import com.business.gym.data.local.entity.NewsEntity
import com.business.gym.data.api.LocalNews
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NewsRepository(
    private val apiService: NewsApiService,
    private val newsDao: NewsDao
) {
    val allNews: Flow<List<LocalNews>> = newsDao.getAllNews().map { entities ->
        entities.map { LocalNews(id = it.id, title = it.title, content = it.content, url = it.url) }
    }

    suspend fun refreshNews(token: String?) {
        try {
            val authHeader = if (token.isNullOrBlank()) "" else "Bearer $token"
            val news = apiService.getLocalNews(authHeader)
            val entities = news.map { 
                NewsEntity(id = it.id, title = it.title, content = it.content, url = it.url) 
            }
            newsDao.deleteAll()
            newsDao.insertAll(entities)
        } catch (e: Exception) {
            android.util.Log.e("NewsRepository", "Failed to refresh news", e)
        }
    }
}
