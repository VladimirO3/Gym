package com.business.gym_app.data.api

import android.content.Context
import com.business.gym_app.ApiService
import com.business.gym_app.util.TokenManagerImpl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private fun getBaseUrl(context: Context): String {
        val globalPref = context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        var savedIp = globalPref.getString("server_ip", "https://verso0100.fvds.ru/") ?: "https://verso0100.fvds.ru/"
        if (savedIp.contains("5.35.98.149", ignoreCase = true) || savedIp.startsWith("http://")) {
            savedIp = "https://verso0100.fvds.ru/"
        }

        return when {
            savedIp.startsWith("https://") -> if (savedIp.endsWith("/")) savedIp else "$savedIp/"
            savedIp.startsWith("http://") -> if (savedIp.endsWith("/")) savedIp else "$savedIp/"
            else -> "https://$savedIp/"
        }
    }

    @Volatile
    private var retrofit: Retrofit? = null

    fun getApiService(context: Context): ApiService {
        val baseUrl = getBaseUrl(context)
        return retrofit?.let { 
            if (it.baseUrl().toString() == baseUrl) it.create(ApiService::class.java) else null
        } ?: synchronized(this) {
            val tokenManager = TokenManagerImpl(context)
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenManager))
                .build()

            val newRetrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit = newRetrofit
            newRetrofit.create(ApiService::class.java)
        }
    }
}
