package com.business.gym.util

import android.util.Log

object AuthUtils {
    const val ADMIN_EMAIL = "verso0100@gmail.com"
    const val GUEST_EMAIL = "guest@gym.app"
    
    /**
     * Проверяет, является ли пользователь главным администратором (Root).
     */
    fun isRootAdmin(email: String?): Boolean {
        if (email == null) return false
        return email.trim().lowercase() == ADMIN_EMAIL.lowercase()
    }

    /**
     * Проверяет, является ли пользователь администратором (включая Root и назначенных).
     * В этой версии метод оставлен для совместимости, но основная роль должна приходить с сервера.
     */
    fun isStaticAdmin(email: String?): Boolean {
        if (email == null) return false
        val normalized = email.trim().lowercase()
        return normalized == ADMIN_EMAIL.lowercase()
    }
}