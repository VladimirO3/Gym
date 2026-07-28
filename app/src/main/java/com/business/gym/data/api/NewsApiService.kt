package com.business.gym.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
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
 * Модель данных сообщения для локального чата.
 */
data class LocalChatMessage(
    val id: Int = 0,
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0
)

/**
 * Модель данных пользователя для локального чата.
 */
data class LocalUser(
    val uid: String = "",
    val email: String = "",
    val name: String = ""
)

/**
 * Модель для входа на сервер.
 */
data class LoginResponse(val token: String)

/**
 * Описание запросов к вашему собственному серверу (Ktor/Node.js/Python).
 */
interface NewsApiService {

    // --- АВТОРИЗАЦИЯ ---
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") pass: String
    ): LoginResponse

    // --- НОВОСТИ ---
    @GET("news")
    suspend fun getLocalNews(
        @Header("Authorization") token: String
    ): List<LocalNews>

    @Multipart
    @POST("news")
    suspend fun postLocalNews(
        @Header("Authorization") token: String,
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part("type") type: RequestBody,
        @Part media: MultipartBody.Part?
    ): Map<String, String>

    @DELETE("news/{id}")
    suspend fun deleteLocalNews(
        @Header("Authorization") token: String,
        @Path("id") id: String
    )

    // --- МУЗЫКА ---
    @GET("tracks")
    suspend fun getLocalTracks(): List<LocalTrack>

    @Multipart
    @POST("tracks")
    suspend fun postLocalTrack(
        @Header("Authorization") token: String,
        @Part("name") name: RequestBody,
        @Part media: MultipartBody.Part?
    ): Map<String, String>

    // --- ЧАТ ---

    // Получение списка доступных собеседников
    @GET("chat/users")
    suspend fun getChatUsers(
        @Header("Authorization") token: String
    ): List<LocalUser>

    // Получение истории сообщений с конкретным пользователем
    @GET("chat/messages/{peerUid}")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("peerUid") peerUid: String
    ): List<LocalChatMessage>

    // Отправка сообщения
    @FormUrlEncoded
    @POST("chat/send")
    suspend fun sendChatMessage(
        @Header("Authorization") token: String,
        @Field("peerUid") peerUid: String,
        @Field("text") text: String
    ): Map<String, String>

    companion object {
        // Базовый адрес по умолчанию
        private var currentBaseUrl = "http://192.168.0.13:5557/"

        fun updateBaseUrl(newUrl: String) {
            currentBaseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        }

        fun create(): NewsApiService {
            return Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
        }
    }
}
