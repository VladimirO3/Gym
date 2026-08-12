package com.business.gym.data.repository

import android.content.Context
import android.util.Log
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.DailyNoteDao
import com.business.gym.data.local.dao.ProfileDao
import com.business.gym.data.local.entity.DailyNoteEntity
import com.business.gym.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
            Log.d("ProfileRepository", "Profile received: ${remote.email}, Remote UID: ${remote.uid}")
            
            // Используем переданный UID как основной, если сервер прислал null
            val finalUid = remote.uid ?: uid
            
            val entity = ProfileEntity(
                uid = finalUid,
                email = remote.email,
                name = remote.name ?: "",
                age = remote.age,
                avatarUrl = remote.avatarUrl,
                themeMode = remote.theme ?: "system",
                lang = remote.lang ?: "system",
                privacyAgreed = remote.privacyAgreed ?: false
            )
            profileDao.insertProfile(entity)
        } catch (e: Exception) {
            Log.e("ProfileRepository", "CRITICAL: Failed to refresh profile. Error: ${e.message}", e)
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
        try {
            val nameBody = (current?.name ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
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
        try {
            val nameBody = (current?.name ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
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
        try {
            val nameBody = (current?.name ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
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
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updateProfileInfo(uid, name, age)
        try {
            val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
            val ageBody = age?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
            val themeBody = current?.themeMode?.toRequestBody("text/plain".toMediaTypeOrNull())
            val langBody = current?.lang?.toRequestBody("text/plain".toMediaTypeOrNull())
            val privacyBody = current?.privacyAgreed?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

            apiService.updateProfile(
                name = nameBody,
                age = ageBody,
                theme = themeBody,
                lang = langBody,
                privacyAgreed = privacyBody
            )
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Update info on VPS failed", e)
        }
    }

    suspend fun updateAvatarUrl(uid: String, url: String?) {
        profileDao.updateAvatarUrl(uid, url)
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
            val remoteNotes = apiService.getNotes()
            Log.d("ProfileRepository", "Notes received: ${remoteNotes.size}")
            remoteNotes.forEach { remote ->
                dailyNoteDao.insertNote(DailyNoteEntity(uid = uid, date = remote.date, note = remote.note))
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "CRITICAL: Failed to refresh notes. Error: ${e.message}", e)
        }
    }

    /**
     * Сохраняет заметку локально и на сервере.
     */
    suspend fun saveNote(note: DailyNoteEntity) {
        dailyNoteDao.insertNote(note)
        try {
            val dateBody = note.date.toRequestBody("text/plain".toMediaTypeOrNull())
            val noteBody = note.note.toRequestBody("text/plain".toMediaTypeOrNull())
            Log.d("ProfileRepository", "Saving note to VPS via Multipart: ${note.date}")
            apiService.saveNote(dateBody, noteBody)
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Failed to save note on VPS", e)
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
