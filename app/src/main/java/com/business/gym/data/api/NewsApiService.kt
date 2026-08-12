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
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import java.io.File

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
    val isRead: Boolean = false,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String? = null // "image" or "video"
)

/**
 * Модель данных пользователя для локального чата.
 */
data class LocalUser(
    @SerializedName("uid", alternate = ["id", "user_id"]) val uid: String = "",
    val email: String = "",
    val name: String = "",
    val age: Int? = null,
    @SerializedName("avatar_url", alternate = ["avatarUrl"]) val avatarUrl: String? = null,
    @SerializedName("last_seen", alternate = ["lastSeen"]) val lastSeen: Long? = null
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
    @SerializedName("description") val description: String,
    @SerializedName("image_url", alternate = ["imageUrl", "url"]) val imageUrl: String = ""
)

/**
 * Модели для магазина.
 */
data class ProductResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: String,
    @SerializedName("description") val description: String,
    @SerializedName("image_url", alternate = ["imageUrl", "url"]) val imageUrl: String
)

/**
 * Модели для профиля и заметок.
 */
data class ProfileResponse(
    @SerializedName("uid", alternate = ["id", "user_id"]) val uid: String?, 
    val email: String,
    val name: String?,
    val age: Int?,
    @SerializedName("avatar_url", alternate = ["avatarUrl"]) val avatarUrl: String?,
    val theme: String?,
    val lang: String?,
    @SerializedName("privacy_agreed") val privacyAgreed: Boolean?
)

data class DailyNoteResponse(
    val date: String,
    val note: String
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

    // --- МАГАЗИН ---
    @GET("shop/products")
    suspend fun getProducts(): List<ProductResponse>

    @Multipart
    @POST("admin/shop/products")
    suspend fun addProduct(
        @Part("name") name: RequestBody,
        @Part("price") price: RequestBody,
        @Part("description") description: RequestBody,
        @Part file: MultipartBody.Part
    ): okhttp3.ResponseBody

    @Multipart
    @POST("admin/shop/products/{id}")
    suspend fun updateProduct(
        @Path("id") id: Int,
        @Part("name") name: RequestBody,
        @Part("price") price: RequestBody,
        @Part("description") description: RequestBody,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/shop/products/{id}")
    suspend fun deleteProduct(
        @Path("id") id: Int
    ): okhttp3.ResponseBody

    @DELETE("admin/shop/products/{id}/photo")
    suspend fun deleteProductPhoto(
        @Path("id") id: Int
    ): okhttp3.ResponseBody

    // --- КОРЗИНА ---
    @GET("cart")
    suspend fun getCart(): List<CartItemResponse>

    @POST("cart")
    suspend fun saveCart(
        @Body items: List<CartItemRequest>
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("register")
    suspend fun register(
        @Field("email") email: String,
        @Field("password") pass: String,
        @Field("phone") phone: String,
        @Field("name") name: String,
        @Field("privacy_agreed") agreed: Boolean
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
    suspend fun getPendingUsers(): List<LocalUser>

    @FormUrlEncoded
    @POST("admin/approve-user")
    suspend fun approveUser(
        @Field("uid") userUid: String? = null,
        @Field("email") email: String? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/users/{uid}")
    suspend fun deleteUser(
        @Path("uid") uid: String,
        @Query("uid") uidQuery: String? = null,
        @Query("email") emailQuery: String? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/users")
    suspend fun deleteUserByEmail(
        @Query("email") email: String
    ): okhttp3.ResponseBody

    // --- НОВОСТИ ---
    @GET("news")
    suspend fun getLocalNews(): List<LocalNews>

    @Multipart
    @POST("admin/news")
    suspend fun postLocalNews(
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part("type") type: RequestBody,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/news/{id}")
    suspend fun deleteLocalNews(
        @Path("id") id: String
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("news/{id}/react")
    suspend fun postReaction(
        @Path("id") id: String,
        @Field("type") type: String
    ): okhttp3.ResponseBody

    // --- МУЗЫКА ---
    @GET("tracks")
    suspend fun getLocalTracks(): List<LocalTrack>

    @Multipart
    @POST("admin/tracks")
    suspend fun postLocalTrack(
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part
    ): okhttp3.ResponseBody

    @DELETE("admin/tracks/{id}")
    suspend fun deleteLocalTrack(
        @Path("id") id: String
    ): okhttp3.ResponseBody

    // --- ЧАТ ---

    // Получение списка доступных собеседников
    @GET("chat/users")
    suspend fun getChatUsers(): List<LocalUser>

    // Получение истории сообщений с конкретным пользователем
    @GET("chat/messages/{peerUid}")
    suspend fun getChatMessages(
        @Path("peerUid") peerUid: String,
        @Query("offset") offset: Int = 0
    ): List<LocalChatMessage>

    // Отправка сообщения
    @Multipart
    @POST("chat/send")
    suspend fun sendChatMessage(
        @Part("peerUid") receiverId: RequestBody,
        @Part("text") message: RequestBody
    ): Map<String, String>

    @Multipart
    @POST("chat/send")
    suspend fun sendChatMedia(
        @Part("peerUid") receiverId: RequestBody,
        @Part("text") message: RequestBody,
        @Part file: MultipartBody.Part
    ): Map<String, String>

    @GET("chat/unread-count")
    suspend fun getUnreadCount(): Map<String, Int>

    @DELETE("chat/messages/{peerUid}")
    suspend fun deleteChat(
        @Path("peerUid") peerUid: String
    ): okhttp3.ResponseBody

    // --- ПРОФИЛЬ ---
    @GET("profile")
    suspend fun getProfile(): ProfileResponse

    @Multipart
    @POST("profile/avatar")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part
    ): okhttp3.ResponseBody

    @Multipart
    @POST("profile/update")
    suspend fun updateProfile(
        @Part("name") name: RequestBody,
        @Part("age") age: RequestBody?,
        @Part("theme") theme: RequestBody? = null,
        @Part("lang") lang: RequestBody? = null,
        @Part("privacy_agreed") privacyAgreed: RequestBody? = null
    ): okhttp3.ResponseBody

    // --- ЗАМЕТКИ (КАЛЕНДАРЬ) ---
    @GET("profile/notes")
    suspend fun getNotes(): List<DailyNoteResponse>

    @FormUrlEncoded
    @POST("profile/notes")
    suspend fun saveNote(
        @Field("date") date: String,
        @Field("content") text: String
    ): okhttp3.ResponseBody

    @DELETE("profile/notes/{date}")
    suspend fun deleteNote(
        @Path("date") date: String
    ): okhttp3.ResponseBody

    @GET("auth/status")
    suspend fun getAuthStatus(): Map<String, String>

    // --- ГЛОБАЛЬНЫЙ КОНТЕНТ (ОФЕРТА) ---
    @GET("content/privacy")
    suspend fun getPrivacyPolicy(): Map<String, String>

    @FormUrlEncoded
    @POST("admin/privacy")
    suspend fun updatePrivacyPolicy(
        @Field("date") date: String,
        @Field("content") content: String
    ): okhttp3.ResponseBody

    companion object {
        // Базовый адрес по умолчанию (VPS)
        private var currentBaseUrl = "http://5.35.98.149:5557/"
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
            val serverIp = settingsPref.getString("server_ip", "5.35.98.149:5557") ?: "5.35.98.149:5557"
            val cleanIp = serverIp.removePrefix("http://").removePrefix("https://").removeSuffix("/")
            
            val base = "http://$cleanIp"
            val cleanRaw = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
            return base + cleanRaw
        }

        private var cachedClient: okhttp3.OkHttpClient? = null
        
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        @Volatile
        private var exoCache: Any? = null 

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        @Synchronized
        private fun getCache(context: android.content.Context): SimpleCache {
            if (exoCache == null) {
                val cacheDir = File(context.cacheDir, "exo_video_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100MB
                val databaseProvider = StandaloneDatabaseProvider(context)
                exoCache = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            return exoCache as SimpleCache
        }

        fun getOkHttpClient(context: android.content.Context): okhttp3.OkHttpClient {
            cachedClient?.let { return it }

            val dispatcher = okhttp3.Dispatcher().apply {
                maxRequestsPerHost = 20 
            }

            val client = okhttp3.OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val url = request.url.toString()
                    
                    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val token = sharedPref.getString("user_session_token", null)
                    
                    // Добавляем токен для всех запросов к API, 
                    // кроме тех, где он уже есть или если это гость
                    val isGuestToken = token == "guest_token"
                    val hasAuth = request.header("Authorization") != null
                    
                    val newRequest = if (!hasAuth && token != null && !isGuestToken) {
                        Log.d("NewsApiService", "Adding Authorization header to: $url")
                        request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        request
                    }
                    
                    val response = chain.proceed(newRequest)
                    Log.d("NewsApiService", "Request: ${request.method} ${request.url} -> Response Code: ${response.code}")
                    if (!response.isSuccessful) {
                        Log.e("NewsApiService", "Error body: ${response.peekBody(1024).string()}")
                    }
                    response
                }
                .authenticator { _, response ->
                    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val refreshToken = sharedPref.getString("user_session_refresh_token", null)
                    
                    // Если получили 401 и есть токен обновления
                    if (response.code == 401 && refreshToken != null) {
                        Log.w("NewsApiService", "401 Unauthorized detected. Attempting token refresh...")
                        
                        synchronized(this) {
                            // Повторно читаем токен, возможно другой поток его уже обновил
                            val currentToken = sharedPref.getString("user_session_token", null)
                            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                            
                            if (requestToken != currentToken && currentToken != null) {
                                // Токен уже был обновлен другим потоком, просто повторяем запрос с новым токеном
                                return@authenticator response.request.newBuilder()
                                    .header("Authorization", "Bearer $currentToken")
                                    .build()
                            }

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
                                        Log.i("NewsApiService", "Token refreshed successfully!")
                                        sharedPref.edit()
                                            .putString("user_session_token", newTokens.token)
                                            .putString("user_session_refresh_token", newTokens.refreshToken)
                                            .commit() // Используем commit для немедленной записи
                                        
                                        return@authenticator response.request.newBuilder()
                                            .header("Authorization", "Bearer ${newTokens.token}")
                                            .build()
                                    }
                                } else {
                                    Log.e("NewsApiService", "Refresh request failed with code: ${refreshResponse.code()}")
                                }
                            } catch (e: Exception) {
                                Log.e("NewsApiService", "CRITICAL: Token refresh exception", e)
                            }
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
            val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
            val token = sharedPref.getString("user_session_token", null)

            // Настройка источника данных с максимально увеличенными тайм-аутами для видео
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)

            if (token != null) {
                httpDataSourceFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            }

            // Использование кэша для видео
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(getCache(context))
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            return DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)
        }

        fun create(context: android.content.Context? = null): NewsApiService {
            if (context != null) {
                cachedService?.let { return it }
                
                val client = getOkHttpClient(context)
                val service = Retrofit.Builder()
                    .baseUrl(currentBaseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(NewsApiService::class.java)
                
                cachedService = service
                return service
            } else {
                // Если контекста нет, возвращаем новый временный сервис без кеширования
                return Retrofit.Builder()
                    .baseUrl(currentBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(NewsApiService::class.java)
            }
        }
    }
}
