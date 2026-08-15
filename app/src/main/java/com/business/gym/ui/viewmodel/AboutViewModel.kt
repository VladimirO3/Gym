package com.business.gym.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.entity.CoachEntity
import com.business.gym.data.repository.CoachRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class AboutViewModel(
    application: Application,
    private val coachRepository: CoachRepository
) : AndroidViewModel(application) {
    private val aboutDatabase = FirebaseDatabase.getInstance().getReference("info_about")
    private val contactDatabase = FirebaseDatabase.getInstance().getReference("info_contact")

    private val _aboutTitle = mutableStateOf("")
    val aboutTitle: State<String> = _aboutTitle

    private val _aboutDescription = mutableStateOf("")
    val aboutDescription: State<String> = _aboutDescription

    private val _aboutServices = mutableStateOf("")
    val aboutServices: State<String> = _aboutServices

    private val _aboutFooter = mutableStateOf("")
    val aboutFooter: State<String> = _aboutFooter

    private val _contactTitle = mutableStateOf("")
    val contactTitle: State<String> = _contactTitle

    private val _contactPhone = mutableStateOf("")
    val contactPhone: State<String> = _contactPhone

    private val _coaches = mutableStateOf(listOf<CoachEntity>())
    val coaches: State<List<CoachEntity>> = _coaches

    private val _isRefreshing = mutableStateOf(false)
    val isRefreshing: State<Boolean> = _isRefreshing

    init {
        fetchAboutInfo()
        fetchContactInfo()
        
        viewModelScope.launch {
            coachRepository.allCoaches.collect { list ->
                android.util.Log.d("AboutViewModel", "Coaches from Room updated: ${list.size}")
                _coaches.value = list
            }
        }
        refreshCoaches()
    }

    private fun fetchAboutInfo() {
        aboutDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _aboutTitle.value = snapshot.child("title").getValue(String::class.java) ?: ""
                _aboutDescription.value = snapshot.child("description").getValue(String::class.java) ?: ""
                _aboutServices.value = snapshot.child("services").getValue(String::class.java) ?: ""
                _aboutFooter.value = snapshot.child("footer").getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchContactInfo() {
        contactDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _contactTitle.value = snapshot.child("title").getValue(String::class.java) ?: ""
                _contactPhone.value = snapshot.child("phone").getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun updateAboutTitle(title: String) = aboutDatabase.child("title").setValue(title)
    fun updateAboutDescription(desc: String) = aboutDatabase.child("description").setValue(desc)
    fun updateAboutServices(services: String) = aboutDatabase.child("services").setValue(services)
    fun updateAboutFooter(footer: String) = aboutDatabase.child("footer").setValue(footer)
    fun updateContactTitle(title: String) = contactDatabase.child("title").setValue(title)
    fun updateContactPhone(phone: String) = contactDatabase.child("phone").setValue(phone)

    fun refreshCoaches() {
        android.util.Log.d("AboutViewModel", "Manual refreshCoaches() triggered")
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = coachRepository.refreshCoaches()
            android.util.Log.d("AboutViewModel", "refreshCoaches result: $result")
            _isRefreshing.value = false
        }
    }

    fun addCoach(name: String, description: String, imagePart: MultipartBody.Part?) {
        android.util.Log.d("AboutViewModel", "addCoach called for: $name")
        viewModelScope.launch {
            coachRepository.addCoach(name, description, imagePart)
        }
    }

    fun updateCoach(id: String, name: String, description: String, imagePart: MultipartBody.Part?) {
        android.util.Log.d("AboutViewModel", "updateCoach called for ID: $id")
        viewModelScope.launch {
            coachRepository.updateCoach(id, name, description, imagePart)
        }
    }

    fun deleteCoach(id: String) {
        viewModelScope.launch {
            coachRepository.deleteCoach(id)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AboutViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = CoachRepository(database.coachDao(), application)
                @Suppress("UNCHECKED_CAST")
                return AboutViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
