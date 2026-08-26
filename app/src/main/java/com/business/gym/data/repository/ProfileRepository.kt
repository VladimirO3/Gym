package com.business.gym.data.repository

import android.content.Context
import android.util.Log
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.DailyNoteDao
import com.business.gym.data.local.dao.ProfileDao
import com.business.gym.data.local.entity.DailyNoteEntity
import com.business.gym.data.local.entity.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Репозиторий для управления данными профиля и заметками.
 * Обеспечивает двустороннюю синхронизацию между локальной БД (Room) и VPS сервером.
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val dailyNoteDao: DailyNoteDao,
    private val context: Context
) {
    private val apiService get() = NewsApiService.create(context)

    fun getProfile(uid: String): Flow<ProfileEntity?> = profileDao.getProfile(uid)

    /**
     * Загружает актуальный профиль с VPS и сохраняет в локальный кэш.
     */
    suspend fun refreshProfileFromServer(uid: String) {
        try {
            Log.d("ProfileRepository", "Refreshing profile from server for UID: $uid")
            val remote = apiService.getProfile()
            Log.d("ProfileRepository", "Profile received: ${remote.email}, Name: ${remote.name}, Age: ${remote.age}")
            
            // Если сервер вернул пустые данные, логируем это
            if (remote.name.isNullOrBlank()) {
                Log.w("ProfileRepository", "Server returned empty name for $uid")
            }

            // Используем тот же алгоритм выбора ID, что и в AuthViewModel: ID > UID > Email
            val finalUid = remote.id?.toString() ?: remote.uid ?: uid
            
            // Сохраняем локальные данные, которые не приходят с сервера (план тренировок)
            val currentLocal = profileDao.getProfile(uid).firstOrNull()
            
            val entity = ProfileEntity(
                uid = finalUid,
                email = remote.email,
                name = remote.name ?: "",
                age = remote.age,
                avatarUrl = remote.avatarUrl,
                themeMode = remote.theme ?: "system",
                lang = remote.lang ?: "system",
                privacyAgreed = remote.privacyAgreed ?: false,
                lastPlanDate = currentLocal?.lastPlanDate,
                dailyPlan = currentLocal?.dailyPlan
            )
            Log.d("ProfileRepository", "Saving profile to Room. UID: $finalUid, Name: ${entity.name}")
            profileDao.insertProfile(entity)
            
            // Если мы сохранили под другим ID, чем просили, сохраняем и под исходным для совместимости
            if (finalUid != uid) {
                profileDao.insertProfile(entity.copy(uid = uid))
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "CRITICAL: Failed to refresh profile. Error: ${e.message}", e)
            // Показываем ошибку пользователю, чтобы он понимал, почему данные не подгрузились
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Ошибка синхронизации профиля", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun saveProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    /**
     * Обновляет тему оформления и синхронизирует с сервером.
     */
    suspend fun updateTheme(uid: String, mode: String) {
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updateTheme(uid, mode)
        
        val name = current?.name ?: ""
        if (name.isBlank()) {
            Log.w("ProfileRepository", "Skipping VPS theme update: name is empty")
            return
        }
        
        try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val ageBody = current?.age?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val themeBody = mode.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.updateProfile(
                name = nameBody,
                age = ageBody,
                theme = themeBody
            )
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Update theme on VPS failed", e)
        }
    }

    /**
     * Обновляет язык и синхронизирует с сервером.
     */
    suspend fun updateLang(uid: String, lang: String) {
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updateLang(uid, lang)
        
        val name = current?.name ?: ""
        if (name.isBlank()) {
            Log.w("ProfileRepository", "Skipping VPS lang update: name is empty")
            return
        }

        try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val ageBody = current?.age?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val langBody = lang.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.updateProfile(
                name = nameBody,
                age = ageBody,
                lang = langBody
            )
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Update lang on VPS failed", e)
        }
    }

    /**
     * Обновляет согласие с приватностью и синхронизирует с сервером.
     */
    suspend fun updatePrivacy(uid: String, agreed: Boolean) {
        Log.d("ProfileRepository", "Updating privacy to $agreed in Room and VPS for UID: $uid")
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updatePrivacy(uid, agreed)
        
        val name = current?.name ?: ""
        if (name.isBlank()) {
            Log.w("ProfileRepository", "Skipping VPS privacy update: name is empty")
            return
        }

        try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val ageBody = current?.age?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val privacyBody = agreed.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            
            apiService.updateProfile(
                name = nameBody,
                age = ageBody,
                privacyAgreed = privacyBody
            )
            Log.i("ProfileRepository", "Privacy update SUCCESS on VPS")
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Update privacy on VPS failed", e)
        }
    }

    /**
     * Обновляет имя и возраст и синхронизирует с сервером.
     */
    suspend fun updateProfileInfo(uid: String, name: String, age: Int?) {
        if (name.isBlank()) {
            throw IllegalArgumentException("name не может быть пустым")
        }

        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updateProfileInfo(uid, name, age)
        try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            // Если возраст не указан, отправляем пустую строку или 0
            val ageValue = age?.toString() ?: ""
            val ageBody = ageValue.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val themeBody = (current?.themeMode ?: "system").toRequestBody("text/plain".toMediaTypeOrNull())
            val langBody = (current?.lang ?: "system").toRequestBody("text/plain".toMediaTypeOrNull())
            val privacyBody = (current?.privacyAgreed ?: false).toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val avatarBody = current?.avatarUrl?.toRequestBody("text/plain".toMediaTypeOrNull())

            Log.d("ProfileRepository", "Updating profile info on VPS for UID: $uid (Name: $name, Age: $ageValue)")
            apiService.updateProfile(
                name = nameBody,
                age = ageBody,
                theme = themeBody,
                lang = langBody,
                privacyAgreed = privacyBody,
                avatarUrl = avatarBody
            )
            Log.i("ProfileRepository", "VPS profile info update SUCCESS")
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Update info on VPS failed", e)
            throw e // Пробрасываем ошибку, чтобы ViewModel могла показать Toast
        }
    }

    suspend fun updateAvatarUrl(uid: String, url: String?) {
        Log.d("ProfileRepository", "Updating avatarUrl in Room for UID: $uid to: $url")
        profileDao.updateAvatarUrl(uid, url)
    }

    suspend fun updateDailyPlan(uid: String, date: String, plan: String) {
        profileDao.updateDailyPlan(uid, date, plan)
    }

    // --- ГЛОБАЛЬНЫЙ КОНТЕНТ ---

    suspend fun getPrivacyPolicy(): String {
        return try {
            val response = apiService.getPrivacyPolicy()
            response["content"] ?: ""
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Failed to fetch privacy policy", e)
            ""
        }
    }

    suspend fun updatePrivacyPolicy(content: String) {
        try {
            val dateStr = java.time.LocalDate.now().toString()
            Log.d("ProfileRepository", "Updating privacy policy on VPS. Date: $dateStr")
            apiService.updatePrivacyPolicy(dateStr, content)
            Log.i("ProfileRepository", "Privacy policy updated on VPS SUCCESS")
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Failed to update privacy policy", e)
        }
    }

    // --- DAILY NOTES (SYNC) ---

    fun getNote(uid: String, date: String): Flow<DailyNoteEntity?> = dailyNoteDao.getNote(uid, date)
    
    fun getAllNotes(uid: String): Flow<List<DailyNoteEntity>> = dailyNoteDao.getAllNotes(uid)

    /**
     * Загружает все заметки пользователя с сервера.
     */
    suspend fun refreshNotesFromServer(uid: String) {
        try {
            Log.d("ProfileRepository", "Refreshing notes from server for UID: $uid")
            val remoteNotes: List<com.business.gym.data.api.DailyNoteResponse>? = apiService.getNotes()
            Log.d("ProfileRepository", "Notes received: ${remoteNotes?.size ?: 0}")
            remoteNotes?.forEach { remote ->
                dailyNoteDao.insertNote(DailyNoteEntity(uid = uid, date = remote.date, note = remote.content))
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "CRITICAL: Failed to refresh notes. Error: ${e.message}", e)
        }
    }

    /**
     * Сохраняет заметку локально и на сервере.
     */
    suspend fun saveNote(note: DailyNoteEntity) {
        // 1. Сначала в локальную БД для быстрого отклика
        dailyNoteDao.insertNote(note)
        
        try {
            // 2. Затем на VPS через FormUrlEncoded
            Log.d("ProfileRepository", "Syncing note to VPS: ${note.date}")
            apiService.saveNote(note.date, note.note)
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Failed to sync note to VPS. Local copy remains.", e)
        }
    }

    /**
     * Удаляет заметку локально и на сервере.
     */
    suspend fun deleteNote(uid: String, date: String) {
        dailyNoteDao.deleteNote(uid, date)
        try {
            apiService.deleteNote(date)
        } catch (e: Exception) {}
    }
}
