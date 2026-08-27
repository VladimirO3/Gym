package com.business.gym.data.api

import android.content.Context
import com.business.gym.ApiService
import com.business.gym.util.TokenManagerImpl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private fun getBaseUrl(context: Context): String {
        val globalPref = context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
        val savedIp = globalPref.getString("server_ip", "5.35.98.149:5557") ?: "5.35.98.149:5557"
        return if (savedIp.startsWith("http")) savedIp else "http://$savedIp/"
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
