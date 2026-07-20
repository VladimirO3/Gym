package com.business.gym.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.Track
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class PlaylistViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().getReference("playlist")
    private val storage = FirebaseStorage.getInstance().getReference("music")

    private val _tracks = mutableStateOf(listOf<Track>())
    val tracks: State<List<Track>> = _tracks

    private val _isUploading = mutableStateOf(false)
    val isUploading: State<Boolean> = _isUploading

    init {
        fetchTracks()
    }

    private fun fetchTracks() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val trackList = mutableListOf<Track>()
                for (child in snapshot.children) {
                    val track = child.getValue(Track::class.java)
                    if (track != null) trackList.add(track)
                }
                _tracks.value = trackList
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("PlaylistViewModel", "Database error: ${error.message}")
            }
        })
    }

    fun uploadTracks(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        _isUploading.value = true

        uris.forEach { uri ->
            val fileName = getFileName(context, uri) ?: "Track_${UUID.randomUUID()}"
            val fileRef = storage.child("${UUID.randomUUID()}_$fileName")

            Log.d("PlaylistViewModel", "Starting upload: $fileName")

            fileRef.putFile(uri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    fileRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUrl = task.result.toString()
                        val trackId = database.push().key ?: UUID.randomUUID().toString()
                        val newTrack = Track(trackId, downloadUrl, fileName)

                        database.child(trackId).setValue(newTrack)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Трек $fileName загружен", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Ошибка базы данных: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Log.e("PlaylistViewModel", "Upload failed", task.exception)
                        Toast.makeText(context, "Ошибка загрузки: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
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
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    fun addTrackByUrl(name: String, url: String) {
        val trackId = database.push().key ?: UUID.randomUUID().toString()
        val newTrack = Track(trackId, url, name)
        database.child(trackId).setValue(newTrack)
    }

    fun deleteTrack(track: Track) {
        database.child(track.id).removeValue()
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(track.url).delete()
        } catch (e: Exception) {
            Log.e("PlaylistViewModel", "Error deleting file", e)
        }
    }
}