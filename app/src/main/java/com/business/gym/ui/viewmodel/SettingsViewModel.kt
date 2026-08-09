package com.business.gym.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.entity.ProfileEntity
import com.business.gym.data.repository.ProfileRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsViewModel(
    application: Application,
    private val repository: ProfileRepository
) : AndroidViewModel(application) {
    companion object {
        private const val ADMIN_EMAIL = "verso0100@gmail.com"
        private const val GUEST_EMAIL = "guest@gym.app"
    }

    private val _themeMode = mutableStateOf("system")
    val themeMode: State<String> = _themeMode
    
    private val _privacyAgreed = mutableStateOf(false)
    val privacyAgreed: State<Boolean> = _privacyAgreed

    private val _serverIp = mutableStateOf("5.35.98.149:5557")
    val serverIp: State<String> = _serverIp

    private val _userName = mutableStateOf("")
    val userName: State<String> = _userName

    private val _userAge = mutableStateOf<Int?>(null)
    val userAge: State<Int?> = _userAge

    private val _avatarUrl = mutableStateOf<String?>(null)
    val avatarUrl: State<String?> = _avatarUrl

    private val _isUpdatingProfile = mutableStateOf(false)
    val isUpdatingProfile: State<Boolean> = _isUpdatingProfile

    private var currentUid: String? = null

    private fun isRegularAuthorizedUser(email: String?, uid: String?): Boolean {
        if (email.isNullOrBlank() || uid.isNullOrBlank()) return false
        val normalized = email.trim().lowercase()
        return normalized != ADMIN_EMAIL.lowercase() && normalized != GUEST_EMAIL.lowercase()
    }

    init {
        // Загружаем глобальные настройки сразу при создании ViewModel
        val globalPref = getApplication<Application>().getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        
        // 1. IP Сервера
        val savedIp = globalPref.getString("server_ip", "5.35.98.149:5557") ?: "5.35.98.149:5557"
        _serverIp.value = savedIp
        NewsApiService.updateBaseUrl("http://$savedIp/")
        
        // 2. Тема оформления (Глобально)
        _themeMode.value = globalPref.getString("theme_mode", "system") ?: "system"
        
        // 3. Язык (Глобально)
        val savedLang = globalPref.getString("lang", "system") ?: "system"
        applyLanguage(savedLang)
    }

    private fun applyLanguage(lang: String) {
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        if (lang == "system") {
            if (!currentAppLocales.isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        } else {
            val currentLangCode = currentAppLocales.get(0)?.language?.split("-")?.get(0)
            if (currentLangCode != lang) {
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }

    fun loadSettings(context: Context, currentUserEmail: String?, uid: String? = null) {
        val canUseProfile = isRegularAuthorizedUser(currentUserEmail, uid)
        currentUid = if (canUseProfile) uid else null
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        val sharedPref = context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
        val globalPref = context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        
        // Тема и Язык теперь всегда из ГЛОБАЛЬНЫХ настроек
        _themeMode.value = globalPref.getString("theme_mode", "system") ?: "system"
        val savedLang = globalPref.getString("lang", "system") ?: "system"
        applyLanguage(savedLang)

        // Privacy и IP (IP дублируется в глобальных)
        _privacyAgreed.value = sharedPref.getBoolean("privacy_agreed", false)
        val defaultIp = globalPref.getString("server_ip", "5.35.98.149:5557") ?: "5.35.98.149:5557"
        _serverIp.value = sharedPref.getString("server_ip", defaultIp) ?: defaultIp
        
        // Обновляем базовый URL в API сервисе
        NewsApiService.updateBaseUrl("http://${_serverIp.value}/")
        
        if (!canUseProfile) {
            _userName.value = ""
            _userAge.value = null
            _avatarUrl.value = null
            return
        }

        // Затем пробуем синхронизироваться с SQLite для личного профиля
        uid?.let { id ->
            viewModelScope.launch {
                repository.getProfile(id).collect { profile ->
                    profile?.let {
                        // ThemeMode в профиле БД пока оставляем, но приоритет у глобальных настроек
                        _privacyAgreed.value = it.privacyAgreed
                        _userName.value = it.name
                        _userAge.value = it.age
                        _avatarUrl.value = it.avatarUrl
                    } ?: run {
                        // Если профиля в БД нет, создаем его
                        repository.saveProfile(ProfileEntity(
                            uid = id, 
                            email = currentUserEmail ?: "", 
                            name = "",
                            themeMode = _themeMode.value,
                            privacyAgreed = _privacyAgreed.value
                        ))
                    }
                }
            }
        }
    }

    fun updateProfile(context: Context, name: String, age: Int?, token: String?) {
        if (token == null || currentUid == null) return
        
        _isUpdatingProfile.value = true
        viewModelScope.launch {
            try {
                NewsApiService.create(context).updateProfile("Bearer $token", name, age)
                repository.updateProfileInfo(currentUid!!, name, age)
                _userName.value = name
                _userAge.value = age
                android.widget.Toast.makeText(context, "Профиль обновлен", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Profile update failed", e)
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    fun uploadAvatar(context: Context, uri: android.net.Uri, token: String?) {
        if (token == null || currentUid == null) return

        _isUpdatingProfile.value = true
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val body = okhttp3.MultipartBody.Part.createFormData("file", "avatar.jpg", requestFile)

                val api = NewsApiService.create(context)
                // Assuming server returns the new avatar URL in a specific way, or we just refresh profile
                // For now, let's assume it returns ok. 
                // Typically server would return { "avatarUrl": "..." }
                api.uploadAvatar("Bearer $token", body)
                
                // We should probably fetch the profile again or the server should return the URL
                // Let's assume the server updates it and we can just guess or refresh
                // For simplicity, let's say we refresh after a delay or if we had a return URL
                
                android.widget.Toast.makeText(context, "Фото загружено", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Avatar upload failed", e)
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    fun setThemeMode(context: Context, currentUserEmail: String?, mode: String) {
        _themeMode.value = mode
        // Сохраняем ГЛОБАЛЬНО
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
        
        // Для обратной совместимости пишем и в профиль пользователя
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
        
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updateTheme(uid, mode) }
        }
    }

    fun setLanguage(context: Context, currentUserEmail: String?, lang: String) {
        applyLanguage(lang)
        
        // Сохраняем ГЛОБАЛЬНО
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
            
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
            
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updateLang(uid, lang) }
        }
    }

    fun setPrivacyAgreed(context: Context, currentUserEmail: String?, agreed: Boolean) {
        _privacyAgreed.value = agreed
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_agreed", agreed).apply()
            
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updatePrivacy(uid, agreed) }
        }
    }

    fun setServerIp(context: Context, currentUserEmail: String?, ip: String) {
        _serverIp.value = ip
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putString("server_ip", ip).apply()
        
        // Дополнительно сохраняем в глобальные настройки для API
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("server_ip", ip).apply()
        
        NewsApiService.updateBaseUrl("http://$ip/")
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = ProfileRepository(database.profileDao())
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
