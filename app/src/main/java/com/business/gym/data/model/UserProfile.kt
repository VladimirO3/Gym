package com.business.gym.data.model

import androidx.annotation.Keep

/**
 * Модель данных профиля пользователя.
 */
@Keep // Аннотация @Keep предотвращает удаление полей при оптимизации кода (R8/Proguard)
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val hasPassword: Boolean = false,
    val avatarUrl: String? = null,
    val lastSeen: Long? = null
)
