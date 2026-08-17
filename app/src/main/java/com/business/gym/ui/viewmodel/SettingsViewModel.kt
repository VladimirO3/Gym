package com.business.gym.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
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
import com.business.gym.data.local.entity.DailyNoteEntity
import com.business.gym.data.local.entity.ProfileEntity
import com.business.gym.data.repository.ProfileRepository
import com.business.gym.util.AuthUtils
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

/**
 * ViewModel для настроек и профиля пользователя.
 * Управляет личными данными, календарем заметок и планом тренировок.
 * Обеспечивает полную синхронизацию данных с VPS сервером.
 */
class SettingsViewModel(
    application: Application,
    private val repository: ProfileRepository
) : AndroidViewModel(application) {
    companion object {
        private const val ADMIN_EMAIL = AuthUtils.ADMIN_EMAIL
        private const val GUEST_EMAIL = AuthUtils.GUEST_EMAIL
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

    // Заметки в календаре
    private val _dailyNotes = mutableStateOf<List<DailyNoteEntity>>(emptyList())
    val dailyNotes: State<List<DailyNoteEntity>> = _dailyNotes

    // Текст Оферты
    private val _privacyPolicyText = mutableStateOf("")
    val privacyPolicyText: State<String> = _privacyPolicyText

    private var currentUid: String? = null

    private fun isRegularAuthorizedUser(email: String?, uid: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val normalized = email.trim().lowercase()
        return normalized != GUEST_EMAIL.lowercase()
    }

    init {
        val globalPref = getApplication<Application>().getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        val savedIp = globalPref.getString("server_ip", "5.35.98.149:5557") ?: "5.35.98.149:5557"
        _serverIp.value = savedIp
        NewsApiService.updateBaseUrl("http://$savedIp/")
        _themeMode.value = globalPref.getString("theme_mode", "system") ?: "system"
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

    fun clearProfile() {
        _userName.value = ""
        _userAge.value = null
        _avatarUrl.value = null
        _dailyNotes.value = emptyList()
        _isUpdatingProfile.value = false
        currentUid = null
    }

    fun loadSettings(context: Context, currentUserEmail: String?, uid: String? = null) {
        val canUseProfile = isRegularAuthorizedUser(currentUserEmail, uid)
        val effectiveUid = when {
            !uid.isNullOrBlank() -> uid
            !currentUserEmail.isNullOrBlank() -> currentUserEmail
            else -> null
        }
        currentUid = if (canUseProfile) effectiveUid else null
        val globalPref = context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        
        _themeMode.value = globalPref.getString("theme_mode", "system") ?: "system"
        val savedLang = globalPref.getString("lang", "system") ?: "system"
        applyLanguage(savedLang)

        if (!canUseProfile || effectiveUid == null) {
            _userName.value = ""
            _userAge.value = null
            _avatarUrl.value = null
            _dailyNotes.value = emptyList()
            return
        }

        val id = effectiveUid
        // Проверка на админа (админам не нужно принимать оферту)
        val isAdmin = AuthUtils.isStaticAdmin(currentUserEmail)
        if (isAdmin) {
            _privacyAgreed.value = true
        }

        // Принудительно запрашиваем актуальные данные профиля с VPS
        viewModelScope.launch {
            repository.refreshProfileFromServer(id)
            if (isAdmin) {
                repository.updatePrivacy(id, true)
            }
        }

        // Подписка на локальный кэш профиля
        viewModelScope.launch {
            repository.getProfile(id).collect { profile ->
                profile?.let {
                    _privacyAgreed.value = if (isAdmin) true else it.privacyAgreed
                    if (it.name.isNotBlank()) _userName.value = it.name
                    if (it.age != null) _userAge.value = it.age
                    
                    // Обновляем URL аватара, даже если он пустой (для сброса)
                    _avatarUrl.value = it.avatarUrl
                    
                    if (it.themeMode != _themeMode.value) _themeMode.value = it.themeMode
                } ?: run {
                    repository.saveProfile(ProfileEntity(
                        uid = id, 
                        email = currentUserEmail ?: "", 
                        name = "",
                        themeMode = _themeMode.value,
                        privacyAgreed = isAdmin
                    ))
                }
            }
        }

        // Загрузка заметок календаря с сервера, затем подписка на локальные
        viewModelScope.launch {
            repository.refreshNotesFromServer(id)
        }

        viewModelScope.launch {
            repository.getAllNotes(id).collect { notes ->
                _dailyNotes.value = notes
            }
        }
    }

    /**
     * Сохранение заметки для выбранной даты.
     */
    fun saveNote(date: LocalDate, text: String) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            repository.saveNote(DailyNoteEntity(uid = uid, date = date.toString(), note = text))
        }
    }

    /**
     * Удаление заметки для даты.
     */
    fun deleteNote(date: LocalDate) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            repository.deleteNote(uid, date.toString())
        }
    }

    fun updateProfile(context: Context, name: String, age: Int?, token: String?) {
        Log.d("SettingsViewModel", "updateProfile called. token=${token?.take(5)}..., uid=$currentUid")
        if (token == null || currentUid == null) return
        
        _isUpdatingProfile.value = true
        viewModelScope.launch {
            try {
                repository.updateProfileInfo(currentUid!!, name, age)
                
                // Важно: Даем серверу немного времени на запись в БД перед обновлением
                kotlinx.coroutines.delay(1000)
                repository.refreshProfileFromServer(currentUid!!)
                
                android.widget.Toast.makeText(context, "Профиль успешно сохранен", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Profile update failed", e)
                android.widget.Toast.makeText(context, "Ошибка сохранения", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    fun uploadAvatar(context: Context, uri: android.net.Uri, token: String?) {
        if (token == null || currentUid == null) {
            Log.e("SettingsViewModel", "Upload avatar failed: token or UID is null")
            return
        }
        _isUpdatingProfile.value = true
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val body = okhttp3.MultipartBody.Part.createFormData("file", "avatar.jpg", requestFile)
                
                // Создаем сервис и явно проверяем, что OkHttpClient будет использовать актуальный токен
                val api = NewsApiService.create(context)
                val response = api.uploadAvatar(body)
                
                Log.i("SettingsViewModel", "Avatar upload response success")
                
                // Сразу обновляем локально для быстрого отклика
                // ВАЖНО: Мы не знаем точно URL до обновления с сервера, но можем запустить refresh
                
                // Даем серверу время обработать файл
                kotlinx.coroutines.delay(1000)
                
                // После успешной загрузки обновляем локальный профиль с сервера
                repository.refreshProfileFromServer(currentUid!!)
                
                // ОБЯЗАТЕЛЬНО: Принудительно уведомляем UI об изменении URL, даже если ссылка та же (для перезагрузки Coil)
                val current = _avatarUrl.value
                _avatarUrl.value = null
                kotlinx.coroutines.delay(50)
                _avatarUrl.value = current
                
                android.widget.Toast.makeText(context, "Фото успешно обновлено", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Avatar upload failed: ${e.message}", e)
                android.widget.Toast.makeText(context, "Ошибка загрузки фото", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    fun setThemeMode(context: Context, currentUserEmail: String?, mode: String) {
        _themeMode.value = mode
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
        
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updateTheme(uid, mode) }
        }
    }

    fun setLanguage(context: Context, currentUserEmail: String?, lang: String) {
        applyLanguage(lang)
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
            
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updateLang(uid, lang) }
        }
    }

    fun setPrivacyAgreed(context: Context, currentUserEmail: String?, agreed: Boolean) {
        _privacyAgreed.value = agreed
        currentUid?.let { uid ->
            viewModelScope.launch { repository.updatePrivacy(uid, agreed) }
        }
    }

    fun setServerIp(context: Context, currentUserEmail: String?, ip: String) {
        _serverIp.value = ip
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("server_ip", ip).apply()
        NewsApiService.updateBaseUrl("http://$ip/")
    }

    /**
     * Загружает текст оферты с сервера.
     */
    fun fetchPrivacyPolicy() {
        viewModelScope.launch {
            val content = repository.getPrivacyPolicy()
            if (content.isNotBlank()) {
                _privacyPolicyText.value = content
            }
        }
    }

    /**
     * Обновляет текст оферты на сервере (только для админа).
     */
    fun updatePrivacyPolicy(content: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updatePrivacyPolicy(content)
            _privacyPolicyText.value = content
            onSuccess()
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = ProfileRepository(database.profileDao(), database.dailyNoteDao(), application)
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
