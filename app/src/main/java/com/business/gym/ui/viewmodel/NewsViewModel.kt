package com.business.gym.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.NewsItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*

import com.business.gym.data.api.NewsApiService
import com.business.gym.data.api.LocalNews
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.*

class NewsViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().getReference("news_items")
    private val storage = FirebaseStorage.getInstance().getReference("news_media")
    private val localApiService = NewsApiService.create()

    private val _newsItems = mutableStateOf(listOf<NewsItem>())
    val newsItems: State<List<NewsItem>> = _newsItems

    private val _localNews = mutableStateOf(listOf<LocalNews>())
    val localNews: State<List<LocalNews>> = _localNews

    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        fetchNews()
        fetchLocalNews(null) // По умолчанию без токена
    }

    fun fetchLocalNews(token: String?) {
        viewModelScope.launch {
            try {
                // Если токен есть, добавляем "Bearer ", как того требует стандарт JWT
                val authHeader = if (token.isNullOrBlank()) "" else "Bearer $token"
                val news = localApiService.getLocalNews(authHeader)
                _localNews.value = news
                Log.d("NewsViewModel", "Loaded ${news.size} news from local server")
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to load from local server: ${e.message}")
            }
        }
    }

    /**
     * Загрузка новости на собственный сервер (API)
     */
    fun uploadToLocalServer(
        context: Context, 
        uri: Uri?, 
        title: String, 
        content: String, 
        token: String,
        onSuccess: () -> Unit
    ) {
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val titlePart = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val contentPart = content.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val type = if (uri != null) {
                    context.contentResolver.getType(uri)?.let { 
                        if (it.contains("video")) "video" else "image"
                    } ?: "image"
                } else "text"
                val typePart = type.toRequestBody("text/plain".toMediaTypeOrNull())

                var mediaPart: MultipartBody.Part? = null
                if (uri != null) {
                    val file = File(context.cacheDir, "upload_tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    val requestFile = file.asRequestBody(context.contentResolver.getType(uri)?.toMediaTypeOrNull())
                    mediaPart = MultipartBody.Part.createFormData("media", file.name, requestFile)
                }

                val response = localApiService.postLocalNews(
                    token = "Bearer $token",
                    title = titlePart,
                    content = contentPart,
                    type = typePart,
                    media = mediaPart
                )
                
                Log.d("NewsViewModel", "Local server response: $response")
                Toast.makeText(context, "Новость добавлена на сервер!", Toast.LENGTH_SHORT).show()
                fetchLocalNews(token)
                onSuccess()
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Local upload failed", e)
                Toast.makeText(context, "Ошибка сервера: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun fetchNews() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val items = mutableListOf<NewsItem>()
                    Log.d("NewsViewModel", "onDataChange: ${snapshot.childrenCount} children")
                    for (child in snapshot.children) {
                        Log.d("NewsViewModel", "Child data: ${child.value}")
                        val item = child.getValue(NewsItem::class.java)
                        if (item != null) {
                            items.add(item)
                        } else {
                            Log.w("NewsViewModel", "Child at ${child.key} was null or couldn't be parsed")
                        }
                    }
                    _newsItems.value = items.sortedByDescending { it.timestamp }
                    Log.d("NewsViewModel", "Successfully parsed ${items.size} news items")
                } catch (e: Exception) {
                    Log.e("NewsViewModel", "Error parsing news items", e)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("NewsViewModel", "Database error: ${error.message}")
            }
        })
    }

    fun uploadMedia(context: Context, uri: Uri) {
        _isUploading.value = true
        Log.d("NewsViewModel", "Starting upload for URI: $uri")
        
        val type = context.contentResolver.getType(uri)?.let { 
            if (it.contains("video")) "video" else "image"
        } ?: "image"

        val originalFileName = getFileName(context, uri) ?: "media_${UUID.randomUUID()}"
        val fileName = "${UUID.randomUUID()}_$originalFileName"
        val fileRef = storage.child(fileName)
        
        fileRef.putFile(uri)
            .addOnProgressListener { taskSnapshot ->
                if (taskSnapshot.totalByteCount > 0) {
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    Log.d("NewsViewModel", "Upload progress: $progress%")
                }
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    Log.e("NewsViewModel", "Upload task failed", task.exception)
                    task.exception?.let { throw it }
                }
                Log.d("NewsViewModel", "Upload successful, getting download URL...")
                fileRef.downloadUrl
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUrl = task.result.toString()
                    Log.d("NewsViewModel", "Got download URL: $downloadUrl")
                    val id = database.push().key ?: UUID.randomUUID().toString()
                    val newItem = NewsItem(id, downloadUrl, type, System.currentTimeMillis())
                    
                    database.child(id).setValue(newItem)
                        .addOnSuccessListener {
                            Log.d("NewsViewModel", "Database update successful")
                            Toast.makeText(context, "Загружено успешно", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e("NewsViewModel", "Database update failed", e)
                            Toast.makeText(context, "Ошибка БД: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.e("NewsViewModel", "Upload failed in complete listener", task.exception)
                    Toast.makeText(context, "Ошибка загрузки: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
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
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    fun addByUrl(url: String, type: String) {
        val id = database.push().key ?: UUID.randomUUID().toString()
        val newItem = NewsItem(id, url, type, System.currentTimeMillis())
        database.child(id).setValue(newItem)
    }

    fun deleteNewsItem(item: NewsItem) {
        database.child(item.id).removeValue()
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(item.url).delete()
        } catch (e: Exception) {}
    }

    /**
     * Удаление новости с локального сервера
     */
    fun deleteLocalNewsItem(id: String, token: String?) {
        if (token == null) return
        viewModelScope.launch {
            try {
                localApiService.deleteLocalNews("Bearer $token", id)
                fetchLocalNews(token)
                Log.d("NewsViewModel", "Local news deleted: $id")
            } catch (e: Exception) {
                Log.e("NewsViewModel", "Failed to delete local news", e)
            }
        }
    }
}
