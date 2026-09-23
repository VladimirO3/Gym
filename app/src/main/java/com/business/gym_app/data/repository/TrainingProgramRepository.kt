package com.business.gym_app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.data.api.TrainingProgramResponse
import com.business.gym_app.data.local.dao.TrainingProgramDao
import com.business.gym_app.data.local.entity.TrainingProgramEntity
import com.business.gym_app.ui.viewmodel.DailyWorkout
import com.business.gym_app.ui.viewmodel.Exercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Программы тренировок: хранятся на VPS в таблице training_programs, локально кэшируются в Room.
 * Если программы нет в Room, она загружается с сервера; локальные изменения отправляются на сервер,
 * при отсутствии сети остаются с флагом pendingSync и досылаются при следующей синхронизации.
 */
class TrainingProgramRepository(
    private val dao: TrainingProgramDao,
    private val context: Context
) {
    private val api get() = NewsApiService.create(context)
    private val gson = Gson()
    // TypeToken.getParameterized НЕ зависит от generic-сигнатуры анонимного класса,
    // поэтому неуязвим к R8/ProGuard ("TypeToken must be created with a type argument").
    private val exerciseListType = TypeToken.getParameterized(List::class.java, Exercise::class.java).type

    enum class SaveResult { SYNCED, LOCAL_ONLY }

    val programs: Flow<List<DailyWorkout>> = dao.getAll().map { list -> list.map { it.toWorkout() } }

    /**
     * Синхронизация с сервером. Возвращает false, если сервер недоступен
     * или у пользователя нет прав администратора (403) — тогда используется Room.
     */
    suspend fun sync(defaults: List<DailyWorkout>): Boolean = mutex.withLock {
        importLegacyPrefs()

        // 1. Досылаем локальные изменения, которые не дошли до сервера
        dao.getAllOnce().filter { it.pendingSync || it.serverId == null }.forEach { entity ->
            try {
                upload(entity)
            } catch (e: Exception) {
                Log.w(TAG, "Pending program ${entity.id} not uploaded: ${e.message}")
            }
        }

        // 2. Загружаем актуальный список с сервера
        val remote = try {
            api.getTrainingPrograms()
        } catch (e: Exception) {
            Log.w(TAG, "Training programs are not available: ${e.message}")
            return@withLock false
        }

        val local = dao.getAllOnce()
        if (remote.isEmpty() && local.isEmpty()) {
            // Первый запуск: сервер и Room пусты — заполняем стандартными программами
            defaults.forEach { workout ->
                val entity = workout.toEntity(serverId = null, createdAt = System.currentTimeMillis(), pendingSync = true)
                dao.upsert(entity)
                runCatching { upload(entity) }
            }
            return@withLock true
        }

        remote.forEach { program ->
            val match = local.find { it.serverId == program.id || it.id == program.localKey() }
            when {
                // Нет в Room — подгружаем с сервера
                match == null -> dao.upsert(program.toEntity(program.localKey()))
                // Есть и без локальных правок — обновляем серверной версией
                !match.pendingSync -> dao.upsert(program.toEntity(match.id))
            }
        }
        // Удалены на сервере (другим администратором)
        local.filter { entity ->
            entity.serverId != null && !entity.pendingSync && remote.none { it.id == entity.serverId }
        }.forEach { dao.delete(it.id) }
        true
    }

    /** Сохраняет программу в Room и отправляет на сервер. */
    suspend fun save(workout: DailyWorkout): SaveResult = mutex.withLock {
        val existing = dao.getById(workout.id)
        val entity = workout.toEntity(
            serverId = existing?.serverId,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            pendingSync = true
        )
        dao.upsert(entity)
        try {
            upload(entity)
            SaveResult.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Program ${workout.id} saved locally only: ${e.message}")
            SaveResult.LOCAL_ONLY
        }
    }

    /** Удаляет программу на сервере и в Room. false — сервер недоступен, программа не удалена. */
    suspend fun delete(id: String): Boolean = mutex.withLock {
        val existing = dao.getById(id) ?: return@withLock true
        existing.serverId?.let { serverId ->
            try {
                api.deleteTrainingProgram(serverId)
            } catch (e: HttpException) {
                if (e.code() != 404) return@withLock false
            } catch (e: Exception) {
                Log.w(TAG, "Delete program $id failed: ${e.message}")
                return@withLock false
            }
        }
        dao.delete(id)
        true
    }

    private suspend fun upload(entity: TrainingProgramEntity) {
        val workout = entity.toWorkout()
        val files = mutableListOf<MultipartBody.Part>()
        val baseUrl = NewsApiService.getBaseUrl()

        // Обложка: локальное фото отправляем файлом, серверное — ссылкой, отсутствие — пустой строкой
        val cover = workout.coverUrl
        val imageUrlField: String? = when {
            cover.isNullOrBlank() -> ""
            isLocalUri(cover) -> {
                val part = readImagePart("image", cover)
                if (part != null) { files += part; null } else ""
            }
            else -> toServerPath(cover, baseUrl)
        }

        val exercises = workout.exercises.mapIndexed { index, ex ->
            val photo = ex.iconUrl.ifBlank { ex.tutorialImageUrl.orEmpty() }
            if (isLocalUri(photo)) {
                readImagePart("exercise_image_$index", photo)?.let { files += it }
                ex.copy(iconUrl = "", tutorialImageUrl = null)
            } else {
                ex.copy(
                    iconUrl = toServerPath(ex.iconUrl, baseUrl),
                    tutorialImageUrl = ex.tutorialImageUrl?.let { toServerPath(it, baseUrl) }
                )
            }
        }

        val clientId = entity.id.toTextBody()
        val title = entity.title.toTextBody()
        val exercisesBody = gson.toJson(exercises).toTextBody()
        val imageUrl = imageUrlField?.toTextBody()

        val response = entity.serverId?.let { serverId ->
            try {
                api.updateTrainingProgram(serverId, clientId, title, exercisesBody, imageUrl, files)
            } catch (e: HttpException) {
                if (e.code() != 404) throw e
                null // удалена на сервере — создаем заново
            }
        } ?: api.createTrainingProgram(clientId, title, exercisesBody, imageUrl, files)

        dao.upsert(response.toEntity(entity.id))
    }

    private fun readImagePart(name: String, uri: String): MultipartBody.Part? = try {
        val parsed = Uri.parse(uri)
        val mime = context.contentResolver.getType(parsed) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        bytes?.let { MultipartBody.Part.createFormData(name, "photo.jpg", it.toRequestBody(mime.toMediaTypeOrNull())) }
    } catch (e: Exception) {
        // Доступ к фото из галереи мог истечь — отправляем программу без него
        Log.w(TAG, "Cannot read image $uri: ${e.message}")
        null
    }

    /** Перенос программ, которые раньше хранились только в SharedPreferences. */
    private suspend fun importLegacyPrefs() {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY, null) ?: return
        try {
            val legacyType = TypeToken.getParameterized(List::class.java, DailyWorkout::class.java).type
            val legacy: List<DailyWorkout> = gson.fromJson(json, legacyType) ?: emptyList()
            legacy.forEach { workout ->
                if (dao.getById(workout.id) == null) {
                    dao.upsert(workout.toEntity(serverId = null, createdAt = System.currentTimeMillis(), pendingSync = true))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import legacy workouts", e)
        }
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    private fun TrainingProgramEntity.toWorkout(): DailyWorkout = DailyWorkout(
        title = title,
        exercises = runCatching { gson.fromJson<List<Exercise>>(exercisesJson, exerciseListType) }.getOrNull().orEmpty(),
        coverUrl = coverUrl,
        id = id
    )

    private fun DailyWorkout.toEntity(serverId: Int?, createdAt: Long, pendingSync: Boolean) = TrainingProgramEntity(
        id = id,
        serverId = serverId,
        title = title,
        coverUrl = coverUrl,
        exercisesJson = gson.toJson(exercises),
        createdAt = createdAt,
        pendingSync = pendingSync
    )

    private fun TrainingProgramResponse.localKey() = clientId?.takeIf { it.isNotBlank() } ?: "server_$id"

    private fun TrainingProgramResponse.toEntity(localId: String) = TrainingProgramEntity(
        id = localId,
        serverId = id,
        title = title.orEmpty(),
        coverUrl = fullUrl(imageUrl),
        exercisesJson = gson.toJson(exercises.orEmpty().map { ex ->
            Exercise(
                name = ex.name.orEmpty(),
                iconUrl = fullUrl(ex.iconUrl).orEmpty(),
                desc = ex.desc.orEmpty(),
                tutorialImageUrl = fullUrl(ex.tutorialImageUrl)
            )
        }),
        createdAt = createdAt ?: System.currentTimeMillis(),
        pendingSync = false
    )

    // Абсолютный адрес нужен, чтобы фото открывались и у пользователя, которому назначена программа
    private fun fullUrl(url: String?): String? =
        url?.takeIf { it.isNotBlank() }?.let { NewsApiService.getFullUrl(context, it) }

    private fun String.toTextBody() = toRequestBody("text/plain".toMediaTypeOrNull())

    companion object {
        private const val TAG = "TrainingProgramRepo"
        private const val LEGACY_PREFS = "custom_workouts_prefs"
        private const val LEGACY_KEY = "custom_workouts_list"
        private val mutex = Mutex()

        fun isLocalUri(url: String?): Boolean =
            url != null && (url.startsWith("content://") || url.startsWith("file://") || url.startsWith("android.resource://"))

        /** https://host/uploads/x.jpg -> /uploads/x.jpg (сервер хранит относительные пути своих файлов). */
        fun toServerPath(url: String, baseUrl: String): String {
            val host = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
            listOf("https://$host", "http://$host").forEach { prefix ->
                if (url.startsWith("$prefix/uploads/")) return url.removePrefix(prefix)
            }
            return url
        }
    }
}
