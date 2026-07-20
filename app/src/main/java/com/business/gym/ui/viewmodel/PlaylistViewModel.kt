package com.business.gym.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun uploadTracks(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        _isUploading.value = true
        var uploadCount = 0
        uris.forEach { uri ->
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            } ?: "Track_${UUID.randomUUID()}"

            val fileRef = storage.child("${UUID.randomUUID()}_$fileName")
            fileRef.putFile(uri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    fileRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val trackId = database.push().key ?: UUID.randomUUID().toString()
                        val newTrack = Track(trackId, task.result.toString(), fileName)
                        database.child(trackId).setValue(newTrack)
                    }
                    uploadCount++
                    if (uploadCount >= uris.size) _isUploading.value = false
                }
        }
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
        } catch (e: Exception) {}
    }
}
