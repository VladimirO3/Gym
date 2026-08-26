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
import com.business.gym.data.api.LocalNews
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.model.NewsItem
import com.business.gym.data.repository.NewsRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * ViewModel для управления лентой новостей.
 * Работает через VPS сервер с поддержкой оффлайн режима через Room.
 */
class NewsViewModel(
    application: Application,
    private val repository: NewsRepository
) : AndroidViewModel(application) {
    
    // API сервис для работы с VPS
    private val localApiService get() = NewsApiService.create(getApplication())

    // Список новостей из локальной БД (VPS синхронизация)
    private val _localNews = mutableStateOf(listOf<LocalNews>())
    val localNews: State<List<LocalNews>> = _localNews

    // Состояние процесса загрузки/публикации
    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        // Подписка на локальный кэш новостей (Room)
        viewModelScope.launch {
            repository.allNews.collect { news ->
                _localNews.value = news
            }
        }
        
        // Фоновое обновление при запуске
        val sharedPref = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("user_session_token", null)
        fetchLocalNews(token)
    }

    /**
     * Загружает/обновляет новости с VPS сервера.
     */
    fun fetchLocalNews(token: String?) {
        viewModelScope.launch {
            try {
                repository.refreshNews(token)
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to fetch local news", e)
            }
        }
    }

    /**
     * Публикация новой новости на VPS сервер (с поддержкой фото/видео).
     */
    fun uploadToLocalServer(
        context: Context, 
        uri: Uri?, 
        title: String, 
        content: String, 
        token: String,
        onSuccess: () -> Unit
    ) {
        Log.d("NewsViewModel", "Starting uploadToLocalServer. Title: $title")
        _isUploading.value = true
        viewModelScope.launch {
            try {
                // Определяем тип контента
                val typeValue = if (uri != null) {
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    if (mimeType.contains("video")) "video" else "image"
                } else "text"

                // Формируем Multipart тело файла
                var filePart: MultipartBody.Part? = null
                if (uri != null) {
                    val contentResolver = context.contentResolver
                    val fileName = getFileName(context, uri) ?: "upload_file"
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val bytes = inputStream.readBytes()
                        val requestFile = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                        filePart = MultipartBody.Part.createFormData("file", fileName, requestFile)
                        inputStream.close()
                    }
                } else {
                    // Пустая заглушка для текстовых новостей, если сервер ожидает файл
                    val requestFile = "".toRequestBody("text/plain".toMediaTypeOrNull())
                    filePart = MultipartBody.Part.createFormData("file", "", requestFile)
                }

                // Запрос к репозиторию для отправки на сервер
                repository.uploadNews(
                    token = token,
                    title = title,
                    content = content,
                    type = typeValue,
                    filePart = filePart
                )
                
                // Сразу обновляем список новостей
                repository.refreshNews(token)
                
                Toast.makeText(context, "Новость успешно добавлена!", Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Local upload failed", e)
                Toast.makeText(context, "Ошибка загрузки на сервер", Toast.LENGTH_LONG).show()
            } finally {
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

    /**
     * Удаление новости с VPS сервера.
     */
    fun deleteLocalNewsItem(id: String, token: String?) {
        if (token == null) return
        viewModelScope.launch {
            try {
                localApiService.deleteLocalNews(id)
                repository.refreshNews(token)
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to delete local news", e)
            }
        }
    }

    /**
     * Добавление новости по прямой ссылке (URL).
     */
    fun addByUrl(url: String, type: String, title: String, content: String) {
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val sharedPref = getApplication<Application>().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val token = sharedPref.getString("user_session_token", null)
                
                if (token != null) {
                    repository.uploadNews(
                        token = token,
                        title = title,
                        content = content,
                        type = type,
                        url = url
                    )
                    repository.refreshNews(token)
                }
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Add by URL failed", e)
            } finally {
                _isUploading.value = false
            }
        }
    }

    // Совместимость с UI
    val newsItems: State<List<NewsItem>> = mutableStateOf(emptyList())

    /**
     * Отправка реакции (лайка) на новость.
     */
    fun reactToNews(id: String, type: String, token: String?) {
        val sharedPref = getApplication<Application>().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val savedToken = sharedPref.getString("user_session_token", null)
        val effectiveToken = if (!token.isNullOrBlank()) token else savedToken

        if (effectiveToken.isNullOrBlank() || effectiveToken == "guest_token") {
            Log.w("NewsViewModel", "Cannot react to news: guest or no token")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("NewsViewModel", "Sending reaction $type for news $id")
                repository.postReaction(effectiveToken, id, type)
                repository.refreshNews(effectiveToken)
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to post reaction for news $id", e)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NewsViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = NewsRepository(database.newsDao(), application)
                @Suppress("UNCHECKED_CAST")
                return NewsViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
