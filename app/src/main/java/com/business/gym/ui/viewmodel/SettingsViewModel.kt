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

class SettingsViewModel(
    application: Application,
    private val repository: ProfileRepository
) : AndroidViewModel(application) {
    private val _themeMode = mutableStateOf("system")
    val themeMode: State<String> = _themeMode
    
    private val _privacyAgreed = mutableStateOf(false)
    val privacyAgreed: State<Boolean> = _privacyAgreed

    private val _serverIp = mutableStateOf("89.108.70.193:5557")
    val serverIp: State<String> = _serverIp

    private var currentUid: String? = null

    fun loadSettings(context: Context, currentUserEmail: String?, uid: String? = null) {
        currentUid = uid
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        val sharedPref = context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
        val globalPref = context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        
        val defaultIp = globalPref.getString("server_ip", "89.108.70.193:5557") ?: "89.108.70.193:5557"

        // Сначала грузим из SharedPreferences для мгновенного отклика
        _themeMode.value = sharedPref.getString("theme_mode", "system") ?: "system"
        _privacyAgreed.value = sharedPref.getBoolean("privacy_agreed", false)
        _serverIp.value = sharedPref.getString("server_ip", defaultIp) ?: defaultIp
        
        // Обновляем базовый URL в API сервисе
        NewsApiService.updateBaseUrl("http://${_serverIp.value}/")
        
        // Затем пробуем синхронизироваться с SQLite если есть UID
        uid?.let { id ->
            viewModelScope.launch {
                repository.getProfile(id).collect { profile ->
                    profile?.let {
                        _themeMode.value = it.themeMode
                        _privacyAgreed.value = it.privacyAgreed
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

        val savedLang = sharedPref.getString("lang", "system") ?: "system"
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        
        if (savedLang == "system") {
            if (!currentAppLocales.isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        } else {
            val currentLangCode = currentAppLocales.get(0)?.language?.split("-")?.get(0)
            if (currentLangCode != savedLang) {
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(savedLang)
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }

    fun setThemeMode(context: Context, currentUserEmail: String?, mode: String) {
        _themeMode.value = mode
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
        
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updateTheme(uid, mode) }
        }
    }

    fun setLanguage(context: Context, currentUserEmail: String?, lang: String) {
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        if (lang == "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
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
