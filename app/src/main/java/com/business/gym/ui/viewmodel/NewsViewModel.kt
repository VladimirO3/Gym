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
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class NewsViewModel(
    application: Application,
    private val repository: NewsRepository
) : AndroidViewModel(application) {
    private val database = FirebaseDatabase.getInstance().getReference("news_items")
    private val storage = FirebaseStorage.getInstance().getReference("news_media")
    private val localApiService get() = NewsApiService.create()

    private val _newsItems = mutableStateOf(listOf<NewsItem>())
    val newsItems: State<List<NewsItem>> = _newsItems

    private val _localNews = mutableStateOf(listOf<LocalNews>())
    val localNews: State<List<LocalNews>> = _localNews

    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        fetchNews()
        
        viewModelScope.launch {
            repository.allNews.collect { news ->
                _localNews.value = news
            }
        }
    }

    fun fetchLocalNews(token: String?) {
        viewModelScope.launch {
            repository.refreshNews(token)
        }
    }

    fun uploadToLocalServer(
        context: Context, 
        uri: Uri?, 
        title: String, 
        content: String, 
        token: String,
        onSuccess: () -> Unit
    ) {
        Log.d("NewsViewModel", "Starting uploadToLocalServer. Title: $title, Content: $content, HasUri: ${uri != null}")
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val typeValue = if (uri != null) {
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    if (mimeType.contains("video")) "video" else "image"
                } else "text"

                var mediaPart: MultipartBody.Part? = null
                if (uri != null) {
                    val originalName = getFileName(context, uri) ?: "file_${UUID.randomUUID()}"
                    val mimeType = context.contentResolver.getType(uri)
                    
                    val file = File(context.cacheDir, "upload_tmp_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    
                    val requestFile = file.asRequestBody(mimeType?.toMediaTypeOrNull())
                    mediaPart = MultipartBody.Part.createFormData("media", originalName, requestFile)
                    Log.d("NewsViewModel", "Created mediaPart: $originalName, mime: $mimeType")
                }

                Log.d("NewsViewModel", "Sending POST to local server...")
                val result = repository.uploadNews(
                    token = token,
                    title = title,
                    content = content,
                    type = typeValue,
                    media = mediaPart
                )
                
                Log.d("NewsViewModel", "Server response: $result")
                
                // Сразу обновляем список новостей
                repository.refreshNews(token)
                
                Log.d("NewsViewModel", "News successfully posted and refreshed")
                Toast.makeText(context, "Новость успешно добавлена!", Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Exception) {
                Log.e("NewsViewModel", "CRITICAL: Local upload failed", e)
                val errorMsg = when (e) {
                    is retrofit2.HttpException -> "Ошибка сервера ${e.code()}: ${e.message()}"
                    is java.net.ConnectException -> "Не удалось подключиться к серверу ${NewsApiService.create().hashCode()}"
                    else -> e.localizedMessage ?: "Неизвестная ошибка"
                }
                Toast.makeText(context, "Ошибка загрузки: $errorMsg", Toast.LENGTH_LONG).show()
            } finally {
                _isUploading.value = false
            }
        }
    }

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

    fun addByUrl(url: String, type: String, title: String = "", content: String = "") {
        val id = database.push().key ?: UUID.randomUUID().toString()
        val newItem = NewsItem(id, url, type, title, content, System.currentTimeMillis())
        database.child(id).setValue(newItem)
    }

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

    fun deleteNewsItem(item: NewsItem) {
        database.child(item.id).removeValue()
        if (item.url.contains("firebasestorage.googleapis.com")) {
            try {
                FirebaseStorage.getInstance().getReferenceFromUrl(item.url).delete()
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Error deleting media", e)
            }
        }
    }

    fun deleteLocalNewsItem(id: String, token: String?) {
        if (token == null) return
        viewModelScope.launch {
            try {
                localApiService.deleteLocalNews("Bearer $token", id)
                repository.refreshNews(token)
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to delete local news", e)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NewsViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = NewsRepository(database.newsDao())
                @Suppress("UNCHECKED_CAST")
                return NewsViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
