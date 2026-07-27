package com.business.gym.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/**
 * Модель данных для локального API.
 */
data class LocalNews(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val url: String = "" // Добавлено поле URL
)

/**
 * Модель данных для локального трека.
 */
data class LocalTrack(
    val id: Int = 0,
    val name: String = "",
    val url: String = ""
)

/**
 * Модель для входа на сервер.
 */
data class LoginResponse(val token: String)

/**
 * Описание запросов к вашему собственному серверу (Ktor/Node.js/Python).
 */
interface NewsApiService {
    
    // Публичный метод для входа (получение JWT токена)
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): LoginResponse

    // Защищенный метод получения новостей
    @GET("news")
    suspend fun getLocalNews(
        @Header("Authorization") token: String
    ): List<LocalNews>

    // Добавление новости на свой сервер (Multipart для фото/видео + текст)
    @Multipart
    @POST("admin/news")
    suspend fun postLocalNews(
        @Header("Authorization") token: String,
        @Part("title") title: okhttp3.RequestBody,
        @Part("content") content: okhttp3.RequestBody,
        @Part("type") type: okhttp3.RequestBody,
        @Part media: okhttp3.MultipartBody.Part?
    ): Map<String, String>

    // Удаление новости с вашего сервера
    @DELETE("admin/news/{id}")
    suspend fun deleteLocalNews(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Map<String, String>

    // --- ПЛЕЙЛИСТ ---

    // Получение списка треков с вашего сервера
    @GET("tracks")
    suspend fun getLocalTracks(): List<LocalTrack>

    // Загрузка трека на ваш сервер
    @Multipart
    @POST("admin/tracks")
    suspend fun postLocalTrack(
        @Header("Authorization") token: String,
        @Part("name") name: okhttp3.RequestBody,
        @Part media: okhttp3.MultipartBody.Part
    ): Map<String, String>

    companion object {
        // IP 10.0.2.2 используется в эмуляторе Android для обращения к "localhost" вашего ПК.
        private const val BASE_URL = "http://10.0.2.2:8080/"

        fun create(): NewsApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
        }
    }
}
