package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class ProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val name: String,
    val age: Int? = null,
    val avatarUrl: String? = null,
    val themeMode: String = "system",
    val lang: String = "system",
    val privacyAgreed: Boolean = false
)
