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
import com.business.gym.data.api.OrderResponse
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.dao.OrderDao
import com.business.gym.data.local.entity.DailyNoteEntity
import com.business.gym.data.local.entity.ProfileEntity
import com.business.gym.data.repository.ProfileRepository
import com.business.gym.util.AuthUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

/**
 * Модели данных для плана тренировок
 */
data class Exercise(val name: String, val iconUrl: String, val desc: String, val tutorialImageUrl: String? = null)
data class DailyWorkout(val title: String, val exercises: List<Exercise>, val coverUrl: String? = null)

/**
 * ViewModel для настроек и профиля пользователя.
 * Управляет личными данными, календарем заметок и планом тренировок.
 * Обеспечивает полную синхронизацию данных с VPS сервером.
 */
class SettingsViewModel(
    application: Application,
    private val repository: ProfileRepository,
    private val orderDao: OrderDao
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

    // История заказов
    private val _orderHistory = mutableStateOf<List<OrderResponse>>(emptyList())
    val orderHistory: State<List<OrderResponse>> = _orderHistory

    private val _dailyPlan = mutableStateOf<String?>(null)
    val dailyPlan: State<String?> = _dailyPlan

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
        val isAdmin = AuthUtils.isStaticAdmin(currentUserEmail)
        Log.d("SettingsViewModel", "loadSettings: effectiveUid=$id, isAdmin=$isAdmin")
        
        if (isAdmin) {
            _privacyAgreed.value = true
        }

        // Принудительно запрашиваем актуальные данные профиля с VPS
        viewModelScope.launch {
            Log.d("SettingsViewModel", "Launching profile refresh for $id")
            repository.refreshProfileFromServer(id)
            if (isAdmin) {
                repository.updatePrivacy(id, true)
            }
        }

        // Подписка на локальный кэш профиля
        viewModelScope.launch {
            repository.getProfile(id).collect { profile ->
                Log.d("SettingsViewModel", "Profile collection update for $id: ${profile?.name ?: "null"}")
                profile?.let {
                    _privacyAgreed.value = if (isAdmin) true else it.privacyAgreed
                    
                    val displayName = if (it.isAdmin || it.role == "admin") {
                        if (AuthUtils.isStaticAdmin(it.email)) "root-администратор"
                        else "админ-${it.name}"
                    } else {
                        it.name
                    }
                    if (it.name.isNotBlank()) _userName.value = displayName

                    if (it.age != null) _userAge.value = it.age
                    
                    // Обновляем URL аватара, даже если он пустой (для сброса)
                    _avatarUrl.value = it.avatarUrl
                    
                    _dailyPlan.value = it.dailyPlan

                    // Проверка и генерация плана тренировок на сегодня
                    val today = LocalDate.now().toString()
                    val planText = it.dailyPlan ?: ""
                    
                    // Считаем количество упражнений в JSON
                    val exerciseCount = try {
                        val workout = Gson().fromJson(planText, DailyWorkout::class.java)
                        workout.exercises.size
                    } catch (e: Exception) { 0 }

                    // Список старых или битых URL, которые мы хотим заменить принудительно
                    val hasOldTutorialUrl = planText.contains("photo-3761705") || 
                                           planText.contains("photo-3768916") ||
                                           planText.contains("photo-2294361") ||
                                           planText.contains("sit-ups.png") ||
                                           planText.contains("cycling.png") ||
                                           planText.contains("pushups.png") ||
                                           planText.contains("images.unsplash.com") ||
                                           planText.contains("images.pexels.com") ||
                                           (planText.contains("img.icons8.com") && !planText.contains("githubusercontent"))

                    val isOldPlan = planText.isNotEmpty() && (exerciseCount < 6 || hasOldTutorialUrl || planText.contains("icons8"))
                    
                    Log.d("SettingsViewModel", "Plan check: today=$today, count=$exerciseCount, isOld=$isOldPlan")

                    if (it.lastPlanDate != today || isOldPlan) {
                        Log.i("SettingsViewModel", "Triggering new plan generation (count=$exerciseCount)")
                        generateDailyPlan(id, today)
                    }
                    
                    if (it.themeMode != _themeMode.value) _themeMode.value = it.themeMode
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

        // Загрузка истории заказов
        viewModelScope.launch {
            orderDao.getUserOrders(id).collect { entities ->
                val gson = Gson()
                _orderHistory.value = entities.map { entity ->
                    OrderResponse(
                        id = entity.orderId,
                        totalPrice = entity.totalPrice,
                        status = entity.status,
                        createdAt = entity.createdAt,
                        items = gson.fromJson(entity.itemsJson, Array<com.business.gym.data.api.CartItemResponse>::class.java).toList()
                    )
                }
            }
        }

        viewModelScope.launch {
            try {
                val api = NewsApiService.create(context)
                val orders = api.getOrders()
                _orderHistory.value = orders
                
                val gson = Gson()
                orders.forEach { order ->
                    orderDao.insertOrder(
                        com.business.gym.data.local.entity.OrderEntity(
                            orderId = order.id,
                            userId = id,
                            totalPrice = order.totalPrice,
                            status = order.status,
                            createdAt = order.createdAt,
                            itemsJson = gson.toJson(order.items)
                        )
                    )
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    Log.i("SettingsViewModel", "Orders endpoint not found, skipping history sync")
                } else {
                    Log.e("SettingsViewModel", "HTTP error fetching orders: ${e.code()}", e)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to fetch orders", e)
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

    private fun generateDailyPlan(uid: String, date: String) {
        val workouts = listOf(
            DailyWorkout(
                title = "Силовая: Ноги и ягодицы", 
                exercises = listOf(
                    Exercise("Приседания", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Bodyweight_Squats/0.jpg", "4 подх. по 10 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Bodyweight_Squats/0.jpg"),
                    Exercise("Жим ногами", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Leg_Press/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Leg_Press/0.jpg"),
                    Exercise("Выпады", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Lunge/0.jpg", "3 подх. по 15 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Lunge/0.jpg"),
                    Exercise("Разгибание ног", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Leg_Extensions/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Leg_Extensions/0.jpg"),
                    Exercise("Сгибание ног", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Seated_Leg_Curl/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Seated_Leg_Curl/0.jpg"),
                    Exercise("Подъем на носки", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Standing_Calf_Raises/0.jpg", "4 подх. по 20 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Standing_Calf_Raises/0.jpg")
                ),
                coverUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Full_Squat/0.jpg"
            ),
            DailyWorkout(
                title = "Верх тела: Грудь и Спина", 
                exercises = listOf(
                    Exercise("Жим лежа", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Bench_Press_-_Medium_Grip/0.jpg", "4 подх. по 8 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Bench_Press_-_Medium_Grip/0.jpg"),
                    Exercise("Тяга блока", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Wide-Grip_Lat_Pulldown/0.jpg", "4 подх. по 10 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Wide-Grip_Lat_Pulldown/0.jpg"),
                    Exercise("Отжимания", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Pushups/0.jpg", "3 подх. до отказа", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Pushups/0.jpg"),
                    Exercise("Разводка гантелей", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Flyes/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Flyes/0.jpg"),
                    Exercise("Тяга гантели", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/One-Arm_Dumbbell_Row/0.jpg", "3 подх. по 10 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/One-Arm_Dumbbell_Row/0.jpg"),
                    Exercise("Гиперэкстензия", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Hyperextensions_With_No_Equipment/0.jpg", "3 подх. по 15 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Hyperextensions_With_No_Equipment/0.jpg")
                ),
                coverUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Incline_Bench_Press_-_Medium_Grip/0.jpg"
            ),
            DailyWorkout(
                title = "Кардио и Выносливость", 
                exercises = listOf(
                    Exercise("Бег", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Run/0.jpg", "30 минут (пульс 130)", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Run/0.jpg"),
                    Exercise("Берпи", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Burpees/0.jpg", "3 подх. по 15 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Burpees/0.jpg"),
                    Exercise("Скакалка", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Jumping_rope/0.jpg", "5 минут интенсивно", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Jumping_rope/0.jpg"),
                    Exercise("Джампинг Джек", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Jumping_Jacks/0.jpg", "3 подх. по 1 мин", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Jumping_Jacks/0.jpg"),
                    Exercise("Альпинист", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Mountain_Climbers/0.jpg", "3 подх. по 45 сек", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Mountain_Climbers/0.jpg"),
                    Exercise("Прыжки на бокс", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Box_Jump/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Box_Jump/0.jpg")
                ),
                coverUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Run/1.jpg"
            ),
            DailyWorkout(
                title = "Пресс и Кор", 
                exercises = listOf(
                    Exercise("Скручивания", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Crunches/0.jpg", "4 подх. по 25 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Crunches/0.jpg"),
                    Exercise("Планка", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Plank/0.jpg", "3 подх. по 1 мин", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Plank/0.jpg"),
                    Exercise("Велосипед", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Air_Bike/0.jpg", "3 подх. по 1 мин", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Air_Bike/0.jpg"),
                    Exercise("Боковая планка", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Side_Plank/0.jpg", "3 подх. по 45 сек", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Side_Plank/0.jpg"),
                    Exercise("Подъем ног", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Lying_Leg_Raises/0.jpg", "3 подх. по 15 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Lying_Leg_Raises/0.jpg"),
                    Exercise("Русский твист", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Russian_Twist/0.jpg", "3 подх. по 20 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Russian_Twist/0.jpg")
                ),
                coverUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Crunches/1.jpg"
            ),
            DailyWorkout(
                title = "Руки: Бицепс и Трицепс", 
                exercises = listOf(
                    Exercise("Подъем гантелей", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Shoulder_Press/0.jpg", "4 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Shoulder_Press/0.jpg"),
                    Exercise("Обратные отжимания", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dips_-_Triceps_Version/0.jpg", "3 подх. по 15 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dips_-_Triceps_Version/0.jpg"),
                    Exercise("Молотки", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Hammer_Curls/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Hammer_Curls/0.jpg"),
                    Exercise("Франц. жим", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/EZ-Bar_Skullcrusher/0.jpg", "3 подх. по 10 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/EZ-Bar_Skullcrusher/0.jpg"),
                    Exercise("Конц. подъем", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Concentration_Curls/0.jpg", "3 подх. по 12 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Concentration_Curls/0.jpg"),
                    Exercise("Разгибания рук", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Triceps_Pushdown/0.jpg", "3 подх. по 15 раз", "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Triceps_Pushdown/0.jpg")
                ),
                coverUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Dumbbell_Shoulder_Press/1.jpg"
            )
        )
        
        val newWorkout = workouts.random()
        val jsonPlan = Gson().toJson(newWorkout)
        
        viewModelScope.launch {
            repository.updateDailyPlan(uid, date, jsonPlan)
            _dailyPlan.value = jsonPlan
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

    @androidx.annotation.OptIn(coil.annotation.ExperimentalCoilApi::class)
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
                
                // ОБЯЗАТЕЛЬНО: Очищаем кэш Coil для этого URL, чтобы изменения отобразились сразу
                val fullUrl = NewsApiService.getFullUrl(context, _avatarUrl.value)
                val imageLoader = coil.ImageLoader(context)
                @OptIn(coil.annotation.ExperimentalCoilApi::class)
                val diskCache = imageLoader.diskCache
                diskCache?.remove(fullUrl)
                imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(fullUrl))
                
                android.widget.Toast.makeText(context, "Фото успешно обновлено", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Avatar upload failed: ${e.message}", e)
                android.widget.Toast.makeText(context, "Ошибка загрузки фото", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    fun deleteAvatar(context: Context) {
        val uid = currentUid ?: return
        _isUpdatingProfile.value = true
        viewModelScope.launch {
            val success = repository.deleteAvatar(uid)
            if (success) {
                _avatarUrl.value = null
                android.widget.Toast.makeText(context, "Фото удалено", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Ошибка при удалении фото", android.widget.Toast.LENGTH_SHORT).show()
            }
            _isUpdatingProfile.value = false
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
                return SettingsViewModel(application, repository, database.orderDao()) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
