package com.business.gym.util

object AuthUtils {
    const val ADMIN_EMAIL = "verso0100@gmail.com"
    const val GUEST_EMAIL = "guest@gym.app"
    
    fun isStaticAdmin(email: String?): Boolean {
        if (email == null) return false
        val normalized = email.trim().lowercase()
        return normalized == ADMIN_EMAIL.lowercase()
    }
}
