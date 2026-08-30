package com.business.gym.util

import android.util.Log

object AuthUtils {
    const val ADMIN_EMAIL = "verso0100@gmail.com"
    const val GUEST_EMAIL = "guest@gym.app"
    
    fun isStaticAdmin(email: String?): Boolean {
        if (email == null) return false
        val normalized = email.trim().lowercase()
        // Проверяем оба варианта почты админа
        val isAdmin = normalized == "verso0100@gmail.com" || normalized == "verso@gmail.com"
        
        Log.d("AuthUtils", "isStaticAdmin check: email='$email', result=$isAdmin")
        return isAdmin
    }
}