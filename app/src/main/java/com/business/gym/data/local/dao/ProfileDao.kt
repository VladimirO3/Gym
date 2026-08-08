package com.business.gym.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.business.gym.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    fun getProfile(uid: String): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Query("UPDATE user_profile SET themeMode = :mode WHERE uid = :uid")
    suspend fun updateTheme(uid: String, mode: String)

    @Query("UPDATE user_profile SET lang = :lang WHERE uid = :uid")
    suspend fun updateLang(uid: String, lang: String)

    @Query("UPDATE user_profile SET privacyAgreed = :agreed WHERE uid = :uid")
    suspend fun updatePrivacy(uid: String, agreed: Boolean)

    @Query("UPDATE user_profile SET name = :name, age = :age WHERE uid = :uid")
    suspend fun updateProfileInfo(uid: String, name: String, age: Int?)

    @Query("UPDATE user_profile SET avatarUrl = :url WHERE uid = :uid")
    suspend fun updateAvatarUrl(uid: String, url: String?)
}
