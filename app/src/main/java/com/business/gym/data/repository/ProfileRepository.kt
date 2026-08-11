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
            Log.d("ProfileRepository", "Refreshing profile from server...")
            val remote = apiService.getProfile()
            Log.d("ProfileRepository", "Profile received: ${remote.email}, UID: ${remote.uid}")
            val entity = ProfileEntity(
                uid = remote.uid,
                email = remote.email,
                name = remote.name,
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
            apiService.updateProfile(
                name = current?.name ?: "",
                age = current?.age,
                theme = mode
            )
        } catch (e: Exception) {}
    }

    /**
     * Обновляет язык и синхронизирует с сервером.
     */
    suspend fun updateLang(uid: String, lang: String) {
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updateLang(uid, lang)
        try {
            apiService.updateProfile(
                name = current?.name ?: "",
                age = current?.age,
                lang = lang
            )
        } catch (e: Exception) {}
    }

    /**
     * Обновляет согласие с приватностью и синхронизирует с сервером.
     */
    suspend fun updatePrivacy(uid: String, agreed: Boolean) {
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updatePrivacy(uid, agreed)
        try {
            apiService.updateProfile(
                name = current?.name ?: "",
                age = current?.age,
                privacyAgreed = agreed
            )
        } catch (e: Exception) {}
    }

    /**
     * Обновляет имя и возраст и синхронизирует с сервером.
     */
    suspend fun updateProfileInfo(uid: String, name: String, age: Int?) {
        val current = profileDao.getProfile(uid).firstOrNull()
        profileDao.updateProfileInfo(uid, name, age)
        try {
            apiService.updateProfile(
                name = name,
                age = age,
                theme = current?.themeMode,
                lang = current?.lang,
                privacyAgreed = current?.privacyAgreed
            )
        } catch (e: Exception) {}
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
            apiService.saveNote(note.date, note.note)
        } catch (e: Exception) {}
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
