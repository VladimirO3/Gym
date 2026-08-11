package com.business.gym.data.repository

import com.business.gym.data.local.dao.DailyNoteDao
import com.business.gym.data.local.dao.ProfileDao
import com.business.gym.data.local.entity.DailyNoteEntity
import com.business.gym.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val dailyNoteDao: DailyNoteDao
) {
    fun getProfile(uid: String): Flow<ProfileEntity?> = profileDao.getProfile(uid)

    suspend fun saveProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    suspend fun updateTheme(uid: String, mode: String) {
        profileDao.updateTheme(uid, mode)
    }

    suspend fun updateLang(uid: String, lang: String) {
        profileDao.updateLang(uid, lang)
    }

    suspend fun updatePrivacy(uid: String, agreed: Boolean) {
        profileDao.updatePrivacy(uid, agreed)
    }

    suspend fun updateProfileInfo(uid: String, name: String, age: Int?) {
        profileDao.updateProfileInfo(uid, name, age)
    }

    suspend fun updateAvatarUrl(uid: String, url: String?) {
        profileDao.updateAvatarUrl(uid, url)
    }

    // Daily Notes
    fun getNote(uid: String, date: String): Flow<DailyNoteEntity?> = dailyNoteDao.getNote(uid, date)
    
    fun getAllNotes(uid: String): Flow<List<DailyNoteEntity>> = dailyNoteDao.getAllNotes(uid)

    suspend fun saveNote(note: DailyNoteEntity) {
        dailyNoteDao.insertNote(note)
    }

    suspend fun deleteNote(uid: String, date: String) {
        dailyNoteDao.deleteNote(uid, date)
    }
}
