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
import com.business.gym.data.repository.AboutRepository
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class AboutViewModel(
    application: Application,
    private val coachRepository: CoachRepository,
    private val aboutRepository: AboutRepository
) : AndroidViewModel(application) {

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
        // Подписка на глобальную инфо из Room
        viewModelScope.launch {
            aboutRepository.globalInfo.collect { info ->
                info?.let {
                    _aboutTitle.value = it.aboutTitle
                    _aboutDescription.value = it.aboutDescription
                    _aboutServices.value = it.aboutServices
                    _aboutFooter.value = it.aboutFooter
                    _contactTitle.value = it.contactTitle
                    _contactPhone.value = it.contactPhone
                }
            }
        }
        
        // Подписка на тренеров из Room
        viewModelScope.launch {
            coachRepository.allCoaches.collect { list ->
                android.util.Log.d("AboutViewModel", "Coaches from Room updated: ${list.size}")
                _coaches.value = list
            }
        }
        
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            aboutRepository.refreshInfo()
            coachRepository.refreshCoaches()
            _isRefreshing.value = false
        }
    }

    fun updateAboutTitle(title: String) = viewModelScope.launch { aboutRepository.updateAboutTitle(title) }
    fun updateAboutDescription(desc: String) = viewModelScope.launch { aboutRepository.updateAboutDescription(desc) }
    fun updateAboutServices(services: String) = viewModelScope.launch { aboutRepository.updateAboutServices(services) }
    fun updateAboutFooter(footer: String) = viewModelScope.launch { aboutRepository.updateAboutFooter(footer) }
    fun updateContactTitle(title: String) = viewModelScope.launch { aboutRepository.updateContactTitle(title) }
    fun updateContactPhone(phone: String) = viewModelScope.launch { aboutRepository.updateContactPhone(phone) }

    fun refreshCoaches() = refreshData()

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
                val coachRepo = CoachRepository(database.coachDao(), application)
                val aboutRepo = AboutRepository(database.globalInfoDao(), application)
                @Suppress("UNCHECKED_CAST")
                return AboutViewModel(application, coachRepo, aboutRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

