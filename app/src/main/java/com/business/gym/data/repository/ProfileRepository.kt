package com.business.gym.data.repository

import com.business.gym.data.local.dao.ProfileDao
import com.business.gym.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val profileDao: ProfileDao) {
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
}
