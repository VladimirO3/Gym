package com.business.gym.data.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Модель данных для локального API.
 */
data class LocalNews(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("url", alternate = ["media_url", "mediaUrl"]) val mediaUrl: String = "",
    @SerializedName("type", alternate = ["media_type", "mediaType"]) val mediaType: String = "image",
    @SerializedName("created_at", alternate = ["createdAt"]) val createdAt: String = "",
    @SerializedName("reactions") val reactions: Map<String, Int> = emptyMap()
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
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val refreshToken: String? = null
)

/**
 * Модели для корзины.
 */
data class CartItemRequest(
    @SerializedName("productId") val productId: Int,
    @SerializedName("quantity") val quantity: Int
)

data class CartItemResponse(
    @SerializedName("productId") val productId: Int,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: String,
    @SerializedName("description") val description: String
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

    // --- КОРЗИНА ---
    @GET("cart")
    suspend fun getCart(
        @Header("Authorization") token: String
    ): List<CartItemResponse>

    @POST("cart")
    suspend fun saveCart(
        @Header("Authorization") token: String,
        @Body items: List<CartItemRequest>
    ): okhttp3.ResponseBody

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
        @Field("email") email: String? = null,
        @Field("phone") phone: String? = null
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("auth/verify-otp")
    suspend fun verifyOtp(
        @Field("email") email: String? = null,
        @Field("phone") phone: String? = null,
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

    @FormUrlEncoded
    @POST("news/{id}/react")
    suspend fun postReaction(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Field("type") type: String
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
        // Базовый адрес по умолчанию (VPS)
        private var currentBaseUrl = "http://89.108.70.193:5557/"
        private var cachedService: NewsApiService? = null

        fun getBaseUrl(): String = currentBaseUrl

        fun updateBaseUrl(newUrl: String) {
            val formattedUrl = when {
                newUrl.startsWith("http://") || newUrl.startsWith("https://") -> newUrl
                else -> "http://$newUrl"
            }
            val finalUrl = if (formattedUrl.endsWith("/")) formattedUrl else "$formattedUrl/"
            
            Log.d("NewsApiService", "Updating Base URL to: $finalUrl (Previous: $currentBaseUrl)")
            
            if (currentBaseUrl != finalUrl) {
                currentBaseUrl = finalUrl
                cachedService = null
                cachedClient = null // Clear client to re-init with new IP if needed
            }
        }

        fun getFullUrl(context: android.content.Context, rawUrl: String?): String {
            if (rawUrl.isNullOrBlank()) return ""
            
            // Если это уже полный URL (начинается с http), возвращаем как есть.
            // Это критически важно для сохранения query-параметров (токенов), 
            // которые сервер теперь добавляет в поле url.
            if (rawUrl.startsWith("http")) return rawUrl
            
            val settingsPref = context.getSharedPreferences("settings_global", android.content.Context.MODE_PRIVATE)
            val serverIp = settingsPref.getString("server_ip", "89.108.70.193:5557") ?: "89.108.70.193:5557"
            val cleanIp = serverIp.removePrefix("http://").removePrefix("https://").removeSuffix("/")
            
            val base = "http://$cleanIp"
            val cleanRaw = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
            return base + cleanRaw
        }

        private var cachedClient: okhttp3.OkHttpClient? = null

        fun getOkHttpClient(context: android.content.Context): okhttp3.OkHttpClient {
            cachedClient?.let { return it }

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val url = request.url.toString()
                    val tokenHeader = request.header("Authorization")
                    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val token = sharedPref.getString("user_session_token", null)
                    
                    // Добавляем токен для всех запросов, КРОМЕ медиафайлов (/uploads/), 
                    // так как для медиа токен теперь передается в самом URL.
                    val isMedia = url.contains("/uploads/")
                    
                    val newRequest = if (tokenHeader == null && token != null && !isMedia) {
                        request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        request
                    }
                    
                    val response = chain.proceed(newRequest)
                    Log.d("NewsApiService", "Request: ${request.url} -> Response: ${response.code}")
                    response
                }
                .authenticator { _, response ->
                    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val refreshToken = sharedPref.getString("user_session_refresh_token", null)
                    
                    if (refreshToken != null && response.code == 401) {
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
                                    sharedPref.edit().apply {
                                        putString("user_session_token", newTokens.token)
                                        putString("user_session_refresh_token", newTokens.refreshToken)
                                        apply()
                                    }
                                    
                                    return@authenticator response.request.newBuilder()
                                        .header("Authorization", "Bearer ${newTokens.token}")
                                        .build()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("NewsApiService", "Token refresh failed", e)
                        }
                    }
                    null
                }
                .build()
            
            cachedClient = client
            return client
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun getMediaSourceFactory(context: android.content.Context): DefaultMediaSourceFactory {
            val dataSourceFactory = DataSource.Factory {
                val httpDataSource = DefaultHttpDataSource.Factory()
                val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                val token = sharedPref.getString("user_session_token", null)
                
                val source = httpDataSource.createDataSource()
                if (token != null) {
                    source.setRequestProperty("Authorization", "Bearer $token")
                }
                source
            }
            return DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        }

        fun create(context: android.content.Context? = null): NewsApiService {
            cachedService?.let { return it }

            val client = if (context != null) getOkHttpClient(context) else okhttp3.OkHttpClient()

            val service = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NewsApiService::class.java)
            
            cachedService = service
            return service
        }
    }
}
