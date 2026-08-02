package com.business.gym.data.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/**
 * Модель данных для локального API.
 */
data class LocalNews(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("url", alternate = ["media_url", "mediaUrl"]) val mediaUrl: String = "",
    @SerializedName("type", alternate = ["media_type", "mediaType"]) val mediaType: String = "image",
    @SerializedName("created_at", alternate = ["createdAt"]) val createdAt: String = ""
)

/**
 * Модель данных для локального трека.
 */
data class LocalTrack(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

/**
 * Модель данных сообщения для локального чата.
 */
data class LocalChatMessage(
    val id: Int = 0,
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false
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
data class LoginResponse(
    val token: String,
    val refreshToken: String? = null
)

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

    @FormUrlEncoded
    @POST("register")
    suspend fun register(
        @Field("email") email: String,
        @Field("password") pass: String,
        @Field("name") name: String
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("auth/request-otp")
    suspend fun requestOtp(
        @Field("email") email: String
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("auth/verify-otp")
    suspend fun verifyOtp(
        @Field("email") email: String,
        @Field("otp") otp: String
    ): LoginResponse

    @FormUrlEncoded
    @POST("auth/refresh")
    fun refreshToken(
        @Field("refreshToken") refreshToken: String
    ): retrofit2.Call<LoginResponse>

    // --- АДМИН-ПАНЕЛЬ (Управление пользователями) ---
    @GET("admin/pending-users")
    suspend fun getPendingUsers(
        @Header("Authorization") token: String
    ): List<LocalUser>

    @FormUrlEncoded
    @POST("admin/approve-user")
    suspend fun approveUser(
        @Header("Authorization") token: String,
        @Field("uid") userUid: String? = null,
        @Field("email") email: String? = null
    ): okhttp3.ResponseBody

    // --- НОВОСТИ ---
    @GET("news")
    suspend fun getLocalNews(): List<LocalNews>

    @Multipart
    @POST("admin/news")
    suspend fun postLocalNews(
        @Header("Authorization") token: String,
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part("type") type: RequestBody,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/news/{id}")
    suspend fun deleteLocalNews(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): okhttp3.ResponseBody

    // --- МУЗЫКА ---
    @GET("tracks")
    suspend fun getLocalTracks(): List<LocalTrack>

    @Multipart
    @POST("admin/tracks")
    suspend fun postLocalTrack(
        @Header("Authorization") token: String,
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part
    ): okhttp3.ResponseBody

    @DELETE("admin/tracks/{id}")
    suspend fun deleteLocalTrack(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): okhttp3.ResponseBody

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
        @Path("peerUid") peerUid: String,
        @Query("offset") offset: Int = 0
    ): List<LocalChatMessage>

    // Отправка сообщения
    @FormUrlEncoded
    @POST("chat/send")
    suspend fun sendChatMessage(
        @Header("Authorization") token: String,
        @Field("receiverId") receiverId: String,
        @Field("message") message: String
    ): Map<String, String>

    @GET("chat/unread-count")
    suspend fun getUnreadCount(
        @Header("Authorization") token: String
    ): Map<String, Int>

    companion object {
        // Базовый адрес по умолчанию
        private var currentBaseUrl = "http://192.168.0.13:5557/"

        fun updateBaseUrl(newUrl: String) {
            currentBaseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        }

        fun create(context: android.content.Context? = null): NewsApiService {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    Log.d("NewsApiService", "Request: ${request.method} ${request.url}")
                    
                    // Если токен уже есть в заголовке, не перезаписываем
                    val tokenHeader = request.header("Authorization")
                    
                    val sharedPref = context?.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val token = sharedPref?.getString("user_session_token", null)
                    
                    val newRequest = if (tokenHeader == null && token != null) {
                        request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        request
                    }
                    
                    val response = chain.proceed(newRequest)
                    Log.d("NewsApiService", "Response: ${response.code} for ${request.url}")
                    response
                }
                .authenticator { _, response ->
                    val sharedPref = context?.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val refreshToken = sharedPref?.getString("user_session_refresh_token", null)
                    
                    if (refreshToken != null && response.code == 401) {
                        Log.w("NewsApiService", "401 Unauthorized - attempting token refresh")
                        try {
                            val api = Retrofit.Builder()
                                .baseUrl(currentBaseUrl)
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()
                                .create(NewsApiService::class.java)
                                
                            val refreshResponse = api.refreshToken(refreshToken).execute()
                            if (refreshResponse.isSuccessful) {
                                val newTokens = refreshResponse.body()
                                if (newTokens != null) {
                                    Log.i("NewsApiService", "Token refreshed successfully")
                                    sharedPref?.edit()?.apply {
                                        putString("user_session_token", newTokens.token)
                                        putString("user_session_refresh_token", newTokens.refreshToken)
                                        apply()
                                    }
                                    
                                    return@authenticator response.request.newBuilder()
                                        .header("Authorization", "Bearer ${newTokens.token}")
                                        .build()
                                }
                            } else {
                                Log.e("NewsApiService", "Token refresh failed: ${refreshResponse.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("NewsApiService", "Token refresh failed with exception", e)
                        }
                    }
                    null
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
        }
    }
}
