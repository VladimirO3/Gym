package com.business.gym_app.ui.viewmodel

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
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.data.api.OrderResponse
import com.business.gym_app.data.local.GymDatabase
import com.business.gym_app.data.local.dao.OrderDao
import com.business.gym_app.data.local.entity.DailyNoteEntity
import com.business.gym_app.data.model.AssignedPrograms
import com.business.gym_app.data.repository.ProfileRepository
import com.business.gym_app.data.repository.TrainingProgramRepository
import com.business.gym_app.util.AuthUtils
import com.business.gym_app.util.AppEventBus
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.util.UUID

/**
 * Модели данных для плана тренировок
 */
data class Exercise(val name: String, val iconUrl: String, val desc: String, val tutorialImageUrl: String? = null)
data class DailyWorkout(
    val title: String, 
    val exercises: List<Exercise>, 
    val coverUrl: String? = null,
    val id: String = UUID.randomUUID().toString()
)

/**
 * ViewModel для настроек и профиля пользователя.
 * Управляет личными данными, календарем заметок и планом тренировок.
 * Обеспечивает полную синхронизацию данных с VPS сервером.
 */
class SettingsViewModel(
    application: Application,
    private val repository: ProfileRepository,
    private val orderDao: OrderDao,
    private val programRepository: TrainingProgramRepository
) : AndroidViewModel(application) {
    companion object {
        private const val ADMIN_EMAIL = AuthUtils.ADMIN_EMAIL
        private const val GUEST_EMAIL = AuthUtils.GUEST_EMAIL
    }

    private val _privacyAgreed = mutableStateOf(false)
    val privacyAgreed: State<Boolean> = _privacyAgreed

    private val _serverIp = mutableStateOf("https://verso0100.fvds.ru/")
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

    // Программы тренировок (созданные администратором)
    private val _customWorkouts = mutableStateOf<List<DailyWorkout>>(emptyList())
    val customWorkouts: State<List<DailyWorkout>> = _customWorkouts

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
        var savedIp = globalPref.getString("server_ip", "https://verso0100.fvds.ru/") ?: "https://verso0100.fvds.ru/"
        
        if (savedIp.contains("5.35.98.149") || savedIp.startsWith("http://")) {
            savedIp = "https://verso0100.fvds.ru/"
            globalPref.edit().putString("server_ip", savedIp).apply()
        }
        
        _serverIp.value = savedIp
        NewsApiService.updateBaseUrl(savedIp)
        val savedLang = globalPref.getString("lang", "system") ?: "system"
        applyLanguage(savedLang)

        // Программы из Room; пока их нет — стандартные
        _customWorkouts.value = getDefaultWorkouts()
        viewModelScope.launch {
            programRepository.programs.collect { programs ->
                _customWorkouts.value = programs.ifEmpty { getDefaultWorkouts() }
            }
        }
        loadCustomWorkouts()
    }

    /** Синхронизирует программы с таблицей training_programs на сервере (доступно администраторам). */
    fun loadCustomWorkouts() {
        viewModelScope.launch {
            programRepository.sync(getDefaultWorkouts())
        }
    }

    fun saveCustomWorkout(context: Context, workout: DailyWorkout, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentList = _customWorkouts.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == workout.id || it.title.trim().equals(workout.title.trim(), ignoreCase = true) }

        if (existingIndex == -1 && currentList.size >= 20) {
            onError("Превышен лимит: можно создать не более 20 программ тренировок")
            return
        }

        if (workout.title.isBlank()) {
            onError("Введите заголовок названия тренировки")
            return
        }

        if (workout.exercises.isEmpty()) {
            onError("Добавьте хотя бы одно упражнение")
            return
        }

        if (workout.exercises.size > 20) {
            onError("Превышен лимит: не более 20 упражнений в одной тренировке")
            return
        }

        // Программа с тем же названием заменяется, а не дублируется
        val toSave = if (existingIndex != -1) workout.copy(id = currentList[existingIndex].id) else workout

        viewModelScope.launch {
            val result = programRepository.save(toSave)
            if (result == TrainingProgramRepository.SaveResult.LOCAL_ONLY) {
                android.widget.Toast.makeText(
                    context,
                    "Нет связи с сервером: программа сохранена на устройстве и будет отправлена позже",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            onSuccess()
        }
    }

    fun deleteCustomWorkout(context: Context, workoutId: String) {
        viewModelScope.launch {
            if (!programRepository.delete(workoutId)) {
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.program_delete_server_error), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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

        // После входа токен уже есть — подтягиваем программы из training_programs
        loadCustomWorkouts()
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
            // Назначения администратора уже сохранены в SharedPrefs —
            // сразу показываем программу админа вместо сгенерированной, не дожидаясь Room
            reapplyAssignedPlan(id)
        }

        // Подписка на локальный кэш профиля
        viewModelScope.launch {
            repository.getProfile(id).collect { profile ->
                Log.d("SettingsViewModel", "Profile collection update for $id: ${profile?.name ?: "null"}")
                profile?.let {
                    _privacyAgreed.value = if (isAdmin) true else it.privacyAgreed
                    
                    // Всегда используем имя из регистрации. 
                    // Статус администратора отображается в UI через специальные индикаторы.
                    if (it.name.isNotBlank()) _userName.value = it.name

                    if (it.age != null) _userAge.value = it.age
                    
                    // Обновляем URL аватара, даже если он пустой (для сброса)
                    _avatarUrl.value = it.avatarUrl
                    
                    // Проверка и генерация плана тренировок на сегодня
                    val today = LocalDate.now().toString()
                    val planText = it.dailyPlan ?: ""

                    // Программы от администратора имеют приоритет: если они назначены,
                    // показываем только их и не генерируем автоматический план.
                    // Запись идёт напрямую в suspend-контексте collect, поэтому эмиссия
                    // Room не перезаписывает назначенную программу сгенерированной.
                    val assignedJson = assignedPlanJson(id)
                    if (assignedJson != null) {
                        setAssignedPlanFlag(id, true)
                        if (_dailyPlan.value != assignedJson) {
                            _dailyPlan.value = assignedJson
                            repository.updateDailyPlan(id, today, assignedJson)
                        }
                        return@let
                    }

                    // Назначений нет — показываем сгенерированный ранее план
                    _dailyPlan.value = planText
                    val wasAssigned = wasAssignedPlan(id)

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
                                           (planText.contains("raw.githubusercontent.com") && !planText.contains("cdn.jsdelivr.net")) ||
                                           planText.contains("ghproxy.net") ||
                                           planText.contains("weserv.nl") ||
                                           (planText.contains("img.icons8.com") && !planText.contains("githubusercontent"))

                    val isOldPlan = planText.isNotEmpty() && (exerciseCount < 6 || hasOldTutorialUrl || planText.contains("icons8"))
                    
                    Log.d("SettingsViewModel", "Plan check: today=$today, count=$exerciseCount, isOld=$isOldPlan")

                    if (it.lastPlanDate != today || isOldPlan || wasAssigned) {
                        Log.i("SettingsViewModel", "Triggering new plan generation (count=$exerciseCount)")
                        generateDailyPlan(id, today)
                    }
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
                        items = gson.fromJson(entity.itemsJson, Array<com.business.gym_app.data.api.CartItemResponse>::class.java).toList()
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
                        com.business.gym_app.data.local.entity.OrderEntity(
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

    fun getDefaultWorkouts(): List<DailyWorkout> {
        val cdn = "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises/"
        fun getExUrl(path: String) = "$cdn$path"

        return listOf(
            DailyWorkout(
                id = "workout_legs",
                title = "Силовая: Ноги и ягодицы", 
                exercises = listOf(
                    Exercise("Приседания", getExUrl("Bodyweight_Squats/0.jpg"), "4 подх. по 10 раз", getExUrl("Bodyweight_Squats/0.jpg")),
                    Exercise("Жим ногами", getExUrl("Leg_Press/0.jpg"), "3 подх. по 12 раз", getExUrl("Leg_Press/0.jpg")),
                    Exercise("Выпады", getExUrl("Dumbbell_Lunge/0.jpg"), "3 подх. по 15 раз", getExUrl("Dumbbell_Lunge/0.jpg")),
                    Exercise("Разгибание ног", getExUrl("Leg_Extensions/0.jpg"), "3 подх. по 12 раз", getExUrl("Leg_Extensions/0.jpg")),
                    Exercise("Сгибание ног", getExUrl("Seated_Leg_Curl/0.jpg"), "3 подх. по 12 раз", getExUrl("Seated_Leg_Curl/0.jpg")),
                    Exercise("Подъем на носки", getExUrl("Standing_Calf_Raises/0.jpg"), "4 подх. по 20 раз", getExUrl("Standing_Calf_Raises/0.jpg"))
                ),
                coverUrl = getExUrl("Barbell_Full_Squat/0.jpg")
            ),
            DailyWorkout(
                id = "workout_upper",
                title = "Верх тела: Грудь и Спина", 
                exercises = listOf(
                    Exercise("Жим лежа", getExUrl("Barbell_Bench_Press_-_Medium_Grip/0.jpg"), "4 подх. по 8 раз", getExUrl("Barbell_Bench_Press_-_Medium_Grip/0.jpg")),
                    Exercise("Тяга блока", getExUrl("Wide-Grip_Lat_Pulldown/0.jpg"), "4 подх. по 10 раз", getExUrl("Wide-Grip_Lat_Pulldown/0.jpg")),
                    Exercise("Отжимания", getExUrl("Pushups/0.jpg"), "3 подх. до отказа", getExUrl("Pushups/0.jpg")),
                    Exercise("Разводка гантелей", getExUrl("Dumbbell_Flyes/0.jpg"), "3 подх. по 12 раз", getExUrl("Dumbbell_Flyes/0.jpg")),
                    Exercise("Тяга гантели", getExUrl("One-Arm_Dumbbell_Row/0.jpg"), "3 подх. по 10 раз", getExUrl("One-Arm_Dumbbell_Row/0.jpg")),
                    Exercise("Гиперэкстензия", getExUrl("Hyperextensions_With_No_Equipment/0.jpg"), "3 подх. по 15 раз", getExUrl("Hyperextensions_With_No_Equipment/0.jpg"))
                ),
                coverUrl = getExUrl("Barbell_Incline_Bench_Press_-_Medium_Grip/0.jpg")
            ),
            DailyWorkout(
                id = "workout_cardio",
                title = "Кардио и Выносливость", 
                exercises = listOf(
                    Exercise("Бег", getExUrl("Run/0.jpg"), "30 минут (пульс 130)", getExUrl("Run/0.jpg")),
                    Exercise("Берпи", getExUrl("Burpees/0.jpg"), "3 подх. по 15 раз", getExUrl("Burpees/0.jpg")),
                    Exercise("Скакалка", getExUrl("Jumping_rope/0.jpg"), "5 минут интенсивно", getExUrl("Jumping_rope/0.jpg")),
                    Exercise("Джампинг Джек", getExUrl("Jumping_Jacks/0.jpg"), "3 подх. по 1 мин", getExUrl("Jumping_Jacks/0.jpg")),
                    Exercise("Альпинист", getExUrl("Mountain_Climbers/0.jpg"), "3 подх. по 45 сек", getExUrl("Mountain_Climbers/0.jpg")),
                    Exercise("Прыжки на бокс", getExUrl("Box_Jump/0.jpg"), "3 подх. по 12 раз", getExUrl("Box_Jump/0.jpg"))
                ),
                coverUrl = getExUrl("Run/1.jpg")
            ),
            DailyWorkout(
                id = "workout_abs",
                title = "Пресс и Кор", 
                exercises = listOf(
                    Exercise("Скручивания", getExUrl("Crunches/0.jpg"), "4 подх. по 25 раз", getExUrl("Crunches/0.jpg")),
                    Exercise("Планка", getExUrl("Plank/0.jpg"), "3 подх. по 1 мин", getExUrl("Plank/0.jpg")),
                    Exercise("Велосипед", getExUrl("Air_Bike/0.jpg"), "3 подх. по 1 мин", getExUrl("Air_Bike/0.jpg")),
                    Exercise("Боковая планка", getExUrl("Side_Plank/0.jpg"), "3 подх. по 45 сек", getExUrl("Side_Plank/0.jpg")),
                    Exercise("Подъем ног", getExUrl("Lying_Leg_Raises/0.jpg"), "3 подх. по 15 раз", getExUrl("Lying_Leg_Raises/0.jpg")),
                    Exercise("Русский твист", getExUrl("Russian_Twist/0.jpg"), "3 подх. по 20 раз", getExUrl("Russian_Twist/0.jpg")),
                ),
                coverUrl = getExUrl("Crunches/1.jpg")
            ),
            DailyWorkout(
                id = "workout_arms",
                title = "Руки: Бицепс и Трицепс", 
                exercises = listOf(
                    Exercise("Подъем гантелей", getExUrl("Dumbbell_Shoulder_Press/0.jpg"), "4 подх. по 12 раз", getExUrl("Dumbbell_Shoulder_Press/0.jpg")),
                    Exercise("Обратные отжимания", getExUrl("Dips_-_Triceps_Version/0.jpg"), "3 подх. по 15 раз", getExUrl("Dips_-_Triceps_Version/0.jpg")),
                    Exercise("Молотки", getExUrl("Hammer_Curls/0.jpg"), "3 подх. по 12 раз", getExUrl("Hammer_Curls/0.jpg")),
                    Exercise("Франц. жим", getExUrl("EZ-Bar_Skullcrusher/0.jpg"), "3 подх. по 10 раз", getExUrl("EZ-Bar_Skullcrusher/0.jpg")),
                    Exercise("Конц. подъем", getExUrl("Concentration_Curls/0.jpg"), "3 подх. по 12 раз", getExUrl("Concentration_Curls/0.jpg")),
                    Exercise("Разгибания рук", getExUrl("Triceps_Pushdown/0.jpg"), "3 подх. по 15 раз", getExUrl("Triceps_Pushdown/0.jpg"))
                ),
                coverUrl = getExUrl("Dumbbell_Shoulder_Press/1.jpg")
            )
        )
    }

    private fun generateDailyPlan(uid: String, date: String) {
        val workouts = _customWorkouts.value.ifEmpty { getDefaultWorkouts() }
        val newWorkout = workouts.random()
        val jsonPlan = Gson().toJson(newWorkout)
        setAssignedPlanFlag(uid, false)

        viewModelScope.launch {
            repository.updateDailyPlan(uid, date, jsonPlan)
            _dailyPlan.value = jsonPlan
        }
    }

    /**
     * Программа, назначенная администратором на сегодня (назначенные программы
     * чередуются по дням), в виде JSON. null — назначений нет, работает автоплан.
     */
    private fun assignedPlanJson(uid: String): String? =
        AssignedPrograms.load(getApplication(), uid)
            ?.programForDay(LocalDate.now().toEpochDay())
            ?.let { Gson().toJson(it) }

    /**
     * Принудительно показывает программу администратора из SharedPrefs поверх
     * сгенерированного плана: SharedPrefs обновляется в refreshProfileFromServer
     * раньше, чем Room-эмиссия приходит в коллектор выше, из-за чего пользователь
     * видел сгенерированный план. Вызывается сразу после refreshProfileFromServer.
     */
    private fun reapplyAssignedPlan(uid: String) {
        val jsonPlan = assignedPlanJson(uid) ?: return
        setAssignedPlanFlag(uid, true)
        _dailyPlan.value = jsonPlan
        viewModelScope.launch {
            repository.updateDailyPlan(uid, LocalDate.now().toString(), jsonPlan)
        }
    }

    // Флаг «текущий план от администратора»: после отмены назначения сразу возвращаем автоплан
    private fun assignedFlagPrefs() =
        getApplication<Application>().getSharedPreferences("assigned_plan_state", Context.MODE_PRIVATE)

    private fun wasAssignedPlan(uid: String) = assignedFlagPrefs().getBoolean("active_$uid", false)

    private fun setAssignedPlanFlag(uid: String, active: Boolean) {
        assignedFlagPrefs().edit().putBoolean("active_$uid", active).apply()
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
                
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.profile_saved), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Profile update failed", e)
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.profile_save_error), android.widget.Toast.LENGTH_SHORT).show()
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
                
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.photo_updated), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Avatar upload failed: ${e.message}", e)
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.photo_upload_error), android.widget.Toast.LENGTH_SHORT).show()
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
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.photo_deleted), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, context.getString(com.business.gym_app.R.string.photo_delete_error), android.widget.Toast.LENGTH_SHORT).show()
            }
            _isUpdatingProfile.value = false
        }
    }

    private val _isChangingPassword = mutableStateOf(false)
    val isChangingPassword: State<Boolean> = _isChangingPassword

    fun changePassword(context: Context, oldPass: String, newPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (oldPass.isBlank() || newPass.isBlank()) {
            onError("Заполните все поля")
            return
        }
        if (newPass.length < 6) {
            onError("Новый пароль должен быть не менее 6 символов")
            return
        }
        _isChangingPassword.value = true
        viewModelScope.launch {
            try {
                val success = repository.changePassword(oldPass, newPass)
                _isChangingPassword.value = false
                if (success) {
                    val sharedPref = context.getSharedPreferences("auth_credentials", Context.MODE_PRIVATE)
                    val savedEmail = sharedPref.getString("saved_email", "") ?: ""
                    if (savedEmail.isNotBlank()) {
                        sharedPref.edit().putString("saved_password", newPass).apply()
                    }
                    onSuccess()
                } else {
                    onError("Ошибка смены пароля. Проверьте правильность текущего пароля.")
                }
            } catch (e: Exception) {
                _isChangingPassword.value = false
                onError(e.message ?: "Ошибка сервера при смене пароля")
            }
        }
    }

    fun setLanguage(context: Context, currentUserEmail: String?, lang: String) {
        applyLanguage(lang)
        context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
        viewModelScope.launch { AppEventBus.emit("LANGUAGE_CHANGED") }
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
        NewsApiService.updateBaseUrl(ip)
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
                val programRepository = TrainingProgramRepository(database.trainingProgramDao(), application)
                return SettingsViewModel(application, repository, database.orderDao(), programRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
