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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * ViewModel для управления лентой новостей.
 * Работает как с Firebase (облако), так и с личным VPS сервером (локальные новости).
 */
class NewsViewModel(
    application: Application,
    private val repository: NewsRepository
) : AndroidViewModel(application) {
    // Ссылка на Firebase Realtime Database для облачных новостей
    private val database = FirebaseDatabase.getInstance().getReference("news_items")
    // Ссылка на Firebase Storage для медиафайлов облачных новостей
    private val storage = FirebaseStorage.getInstance().getReference("news_media")
    
    // API сервис для работы с VPS
    private val localApiService get() = NewsApiService.create(getApplication())

    // Список новостей из Firebase
    private val _newsItems = mutableStateOf(listOf<NewsItem>())
    val newsItems: State<List<NewsItem>> = _newsItems

    // Список новостей из локальной БД (VPS синхронизация)
    private val _localNews = mutableStateOf(listOf<LocalNews>())
    val localNews: State<List<LocalNews>> = _localNews

    // Состояние процесса загрузки/публикации
    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        // Загрузка новостей из Firebase при старте
        fetchNews()
        
        // Подписка на локальный кэш новостей (Room)
        viewModelScope.launch {
            repository.allNews.collect { news ->
                _localNews.value = news
            }
        }
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

    /**
     * Внутренний метод для получения новостей из Firebase в реальном времени.
     */
    private fun fetchNews() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children.mapNotNull { it.getValue(NewsItem::class.java) }
                _newsItems.value = items.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("NewsViewModel", "Database error: ${error.message}")
            }
        })
    }

    /**
     * Добавление новости по прямой ссылке (в Firebase).
     */
    fun addByUrl(url: String, type: String, title: String = "", content: String = "") {
        val id = database.push().key ?: UUID.randomUUID().toString()
        val newItem = NewsItem(id, url, type, title, content, System.currentTimeMillis())
        database.child(id).setValue(newItem)
    }

    /**
     * Загрузка медиа в Firebase Storage.
     */
    fun uploadMedia(context: Context, uri: Uri) {
        _isUploading.value = true
        val type = context.contentResolver.getType(uri)?.let { 
            if (it.contains("video")) "video" else "image"
        } ?: "image"

        val originalFileName = getFileName(context, uri) ?: "media_${UUID.randomUUID()}"
        val fileRef = storage.child("${UUID.randomUUID()}_$originalFileName")
        
        fileRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                fileRef.downloadUrl
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val id = database.push().key ?: UUID.randomUUID().toString()
                    val newItem = NewsItem(
                        id = id, 
                        url = task.result.toString(), 
                        type = type, 
                        title = "", 
                        content = "", 
                        timestamp = System.currentTimeMillis()
                    )
                    database.child(id).setValue(newItem)
                }
                _isUploading.value = false
            }
    }

    /**
     * Вспомогательный метод для получения имени файла из Uri.
     */
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
     * Удаление новости из Firebase.
     */
    fun deleteNewsItem(item: NewsItem) {
        database.child(item.id).removeValue()
        if (item.url.contains("firebasestorage.googleapis.com")) {
            try {
                FirebaseStorage.getInstance().getReferenceFromUrl(item.url).delete()
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Error deleting media from storage", e)
            }
        }
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
     * Отправка реакции (лайка) на новость.
     */
    fun reactToNews(id: String, type: String, token: String?) {
        if (token == null || token == "guest_token") return
        viewModelScope.launch {
            try {
                repository.postReaction(token, id, type)
                repository.refreshNews(token)
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to post reaction", e)
            }
        }
    }

    /**
     * Фабрика для NewsViewModel.
     */
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
