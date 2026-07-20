package com.business.gym.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.NewsItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class NewsViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().getReference("news_items")
    private val storage = FirebaseStorage.getInstance().getReference("news_media")

    private val _newsItems = mutableStateOf(listOf<NewsItem>())
    val newsItems: State<List<NewsItem>> = _newsItems

    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        fetchNews()
    }

    private fun fetchNews() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<NewsItem>()
                for (child in snapshot.children) {
                    val item = child.getValue(NewsItem::class.java)
                    if (item != null) items.add(item)
                }
                _newsItems.value = items.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun uploadMedia(context: Context, uri: Uri) {
        _isUploading.value = true
        val type = if (context.contentResolver.getType(uri)?.contains("video") == true) "video" else "image"
        val fileRef = storage.child("${UUID.randomUUID()}_${uri.lastPathSegment}")
        
        fileRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                fileRef.downloadUrl
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val id = database.push().key ?: UUID.randomUUID().toString()
                    val newItem = NewsItem(id, task.result.toString(), type, System.currentTimeMillis())
                    database.child(id).setValue(newItem)
                }
                _isUploading.value = false
            }
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
}
