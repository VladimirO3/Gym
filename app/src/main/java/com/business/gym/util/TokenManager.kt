package com.business.gym.util

import android.content.Context

interface TokenManager {
    fun getToken(): String?
    fun saveToken(token: String, refreshToken: String? = null)
    fun clearToken()
}

class TokenManagerImpl(private val context: Context) : TokenManager {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    override fun getToken(): String? {
        val token = prefs.getString("user_session_token", null)
        return if (token == "guest_token") null else token
    }

    override fun saveToken(token: String, refreshToken: String?) {
        prefs.edit().apply {
            putString("user_session_token", token)
            if (refreshToken != null) {
                putString("user_session_refresh_token", refreshToken)
            }
            apply()
        }
    }

    override fun clearToken() {
        prefs.edit().clear().apply()
    }
}
