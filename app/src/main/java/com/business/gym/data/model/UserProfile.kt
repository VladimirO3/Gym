package com.business.gym.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import androidx.annotation.Keep

/**
 * Модель данных профиля пользователя.
 *
 * @property uid Уникальный идентификатор пользователя из Firebase Auth.
 * @property email Адрес электронной почты.
 * @property name Отображаемое имя (обычно часть e-mail до символа '@').
 * @property hasPassword Флаг, установлена ли пароль (важно для входа через SMS).
 */
@Keep // Аннотация @Keep предотвращает удаление полей при оптимизации кода (R8/Proguard)
@IgnoreExtraProperties // Игнорирует поля в БД, которых нет в этом классе, предотвращая краши
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val hasPassword: Boolean = false,
    val avatarUrl: String? = null,
    val lastSeen: Long? = null
)
