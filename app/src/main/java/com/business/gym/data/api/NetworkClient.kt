package com.business.gym.data.api

import android.content.Context
import com.business.gym.ApiService
import com.business.gym.util.TokenManagerImpl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val BASE_URL = "http://5.35.98.149:5557/"

    @Volatile
    private var retrofit: Retrofit? = null

    fun getApiService(context: Context): ApiService {
        return retrofit?.create(ApiService::class.java) ?: synchronized(this) {
            val currentRetrofit = retrofit
            if (currentRetrofit != null) {
                currentRetrofit.create(ApiService::class.java)
            } else {
                val tokenManager = TokenManagerImpl(context)
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(tokenManager))
                    .build()

                val newRetrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                retrofit = newRetrofit
                newRetrofit.create(ApiService::class.java)
            }
        }
    }
}
