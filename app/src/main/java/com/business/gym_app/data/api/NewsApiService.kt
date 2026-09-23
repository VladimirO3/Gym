package com.business.gym_app.data.api

import android.util.Log
import androidx.annotation.Keep
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File

/**
 * Модель данных для локального API.
 */
@Keep
data class LocalNews(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("url", alternate = ["media_url", "mediaUrl"]) val mediaUrl: String = "",
    @SerializedName("type", alternate = ["media_type", "mediaType"]) val mediaType: String = "image",
    @SerializedName("created_at", alternate = ["createdAt"]) val createdAt: String = "",
    @SerializedName("user_reaction", alternate = ["userReaction", "my_reaction", "myReaction"]) val userReaction: String? = null,
    @SerializedName("reactions") val reactions: Map<String, Int>? = emptyMap()
)

/**
 * Модель данных для локального трека.
 */
@Keep
data class LocalTrack(
    @SerializedName("id") val id: Int? = 0,
    @SerializedName("name") val name: String? = "",
    @SerializedName("url") val url: String? = "",
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
    @SerializedName("id") val id: String? = null,
    @SerializedName("uid") val uid: String? = null,
    val email: String = "",
    val name: String = "",
    val age: Any? = null,
    @SerializedName("avatar_url", alternate = ["avatarUrl"]) val avatarUrl: String? = null,
    @SerializedName("last_seen", alternate = ["lastSeen"]) val lastSeen: Long? = null,
    @SerializedName("is_admin", alternate = ["isAdmin", "isadmin", "admin", "is_Admin"]) val isAdmin: Any? = false,
    @SerializedName("role", alternate = ["user_role", "userRole", "role_name", "Role"]) val role: Any? = "user"
)

/**
 * Модель для входа на сервер.
 */
data class LoginRequest(
    val email: String,
    val password: String
)

@Keep
data class LoginResponse(
    val token: String,
    val refreshToken: String? = null
)

/**
 * Модели для корзины.
 */
data class CartItemRequest(
    @SerializedName("productId") val productId: Any, // Может быть Int или String. Используем Any для сохранения типа на сервере.
    @SerializedName("quantity") val quantity: Int
)

data class CartItemResponse(
    @SerializedName("productId") val productId: Any, // Может быть Int или String
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("name") val name: String = "",
    @SerializedName("price") val price: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("image_url", alternate = ["imageUrl", "url", "image"]) val imageUrl: String = ""
)

/**
 * Модели для истории заказов.
 */
data class OrderResponse(
    @SerializedName("id") val id: String,
    @SerializedName("totalPrice") val totalPrice: Int,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("items") val items: List<CartItemResponse>
)

data class CreateOrderResponse(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("status") val status: String
)

/**
 * Модели для магазина.
 */
@Keep
data class ProductResponse(
    @SerializedName("id") val id: Any? = null, // Может быть Int или String
    @SerializedName("name") val name: String? = "",
    @SerializedName("price") val price: String? = "",
    @SerializedName("description") val description: String? = "",
    @SerializedName("image_url", alternate = ["imageUrl", "url", "image"]) val imageUrl: String? = ""
)

/**
 * Модели для профиля и заметок.
 */
@Keep
data class ProfileResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("uid") val uid: String? = null, 
    val email: String,
    val name: String?,
    val age: Any?,
    @SerializedName("avatar_url", alternate = ["avatarUrl"]) val avatarUrl: String?,
    val theme: String?,
    val lang: String?,
    @SerializedName("privacy_agreed") val privacyAgreed: Boolean?,
    @SerializedName("is_admin", alternate = ["isAdmin", "isadmin", "admin", "is_Admin"]) val isAdmin: Any? = false,
    @SerializedName("role", alternate = ["user_role", "userRole", "role_name", "Role"]) val role: Any? = "user",
    // Программы, назначенные администратором (JSON, см. AssignedPrograms)
    @SerializedName("daily_plan") val dailyPlan: String? = null
)

/**
 * Программа тренировок из таблицы training_programs (GET/POST/PUT admin/training-programs).
 */
@Keep
data class TrainingProgramResponse(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("exercises") val exercises: List<TrainingExerciseResponse>? = null,
    @SerializedName("created_at") val createdAt: Long? = null
)

@Keep
data class TrainingExerciseResponse(
    @SerializedName("name") val name: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("tutorialImageUrl") val tutorialImageUrl: String? = null
)

data class DailyNoteResponse(
    val date: String,
    val content: String
)

data class GlobalInfoResponse(
    val aboutTitle: String,
    val aboutDescription: String,
    val aboutServices: String,
    val aboutFooter: String,
    val contactTitle: String,
    val contactPhone: String
)

/**
 * Модель для тренеров.
 */
data class CoachResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("image_url", alternate = ["imageUrl", "url"]) val imageUrl: String? = null
)

data class MessageRequest(
    @SerializedName("text", alternate = ["message", "content"]) val text: String
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
    suspend fun getProducts(@Query("lang") language: String? = null): List<ProductResponse>

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
        @Path("id") id: Any,
        @Part("name") name: RequestBody,
        @Part("price") price: RequestBody,
        @Part("description") description: RequestBody,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/shop/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Any): okhttp3.ResponseBody

    @DELETE("admin/shop/products/{id}/photo")
    suspend fun deleteProductPhoto(
        @Path("id") id: Any
    ): okhttp3.ResponseBody

    // --- КОРЗИНА ---
    @GET("cart")
    suspend fun getCart(): List<CartItemResponse>

    @POST("cart")
    suspend fun saveCart(
        @Body items: List<CartItemRequest>
    ): okhttp3.ResponseBody

    @GET("orders")
    suspend fun getOrders(): List<OrderResponse>

    @POST("orders/checkout")
    suspend fun checkout(): CreateOrderResponse

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

    @GET("admin/users/{userId}")
    suspend fun getUserProfile(
        @Path(value = "userId", encoded = true) userId: String
    ): ProfileResponse

    @DELETE("admin/users/{userId}")
    suspend fun deleteUser(
        @Path(value = "userId", encoded = true) userId: String
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("admin/make-admin")
    suspend fun makeAdmin(
        @Field("uid") uid: String? = null,
        @Field("email") email: String? = null
    ): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("admin/remove-admin")
    suspend fun removeAdmin(
        @Field("uid") uid: String? = null,
        @Field("email") email: String? = null
    ): okhttp3.ResponseBody

    @PUT("admin/users/{userId}/update")
    suspend fun adminUpdateProfile(
        @Path(value = "userId", encoded = true) userId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): okhttp3.ResponseBody

    @DELETE("admin/users/{userId}/photo")
    suspend fun adminDeleteUserPhoto(
        @Path(value = "userId", encoded = true) userId: String
    ): okhttp3.ResponseBody

    // --- ПРОГРАММЫ ТРЕНИРОВОК (таблица training_programs) ---
    @GET("admin/training-programs")
    suspend fun getTrainingPrograms(): List<TrainingProgramResponse>

    /** files: image — обложка, exercise_image_{N} — фото N-го упражнения. */
    @Multipart
    @POST("admin/training-programs")
    suspend fun createTrainingProgram(
        @Part("client_id") clientId: RequestBody,
        @Part("title") title: RequestBody,
        @Part("exercises") exercises: RequestBody,
        @Part("image_url") imageUrl: RequestBody?,
        @Part files: List<MultipartBody.Part>
    ): TrainingProgramResponse

    @Multipart
    @PUT("admin/training-programs/{id}")
    suspend fun updateTrainingProgram(
        @Path("id") id: Int,
        @Part("client_id") clientId: RequestBody,
        @Part("title") title: RequestBody,
        @Part("exercises") exercises: RequestBody,
        @Part("image_url") imageUrl: RequestBody?,
        @Part files: List<MultipartBody.Part>
    ): TrainingProgramResponse

    @DELETE("admin/training-programs/{id}")
    suspend fun deleteTrainingProgram(@Path("id") id: Int): okhttp3.ResponseBody

    // --- НОВОСТИ ---
    @GET("news")
    suspend fun getLocalNews(@Query("lang") language: String? = null): List<LocalNews>

    @Multipart
    @POST("admin/news")
    suspend fun postLocalNews(
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part("type") type: RequestBody,
        @Part("url") url: RequestBody? = null,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @Multipart
    @POST("admin/news/{id}")
    suspend fun updateLocalNews(
        @Path("id") id: String,
        @Part("title") title: RequestBody,
        @Part("content") content: RequestBody,
        @Part("type") type: RequestBody,
        @Part("url") url: RequestBody? = null,
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
        @Path(value = "peerUid", encoded = true) peerUid: String,
        @Query("offset") offset: Int = 0
    ): List<LocalChatMessage>

    // Отправка сообщения
    @POST("chat/messages/{peerUid}")
    suspend fun sendChatMessage(
        @Path(value = "peerUid", encoded = true) peerUid: String,
        @Body request: MessageRequest
    ): Map<String, String>

    @Multipart
    @POST("chat/messages/{peerUid}")
    suspend fun sendChatMedia(
        @Path(value = "peerUid", encoded = true) peerUid: String,
        @Part("text") message: RequestBody,
        @Part file: MultipartBody.Part
    ): Map<String, String>

    @GET("chat/unread-count")
    suspend fun getUnreadCount(): Map<String, Int>

    @DELETE("chat/messages/{peerUid}")
    suspend fun deleteChat(
        @Path(value = "peerUid", encoded = true) peerUid: String
    ): okhttp3.ResponseBody

    // --- ПРОФИЛЬ ---
    @GET("profile")
    suspend fun getProfile(): ProfileResponse

    @GET("profile")
    suspend fun getProfileWithToken(
        @Header("Authorization") authHeader: String
    ): ProfileResponse

    @DELETE("profile")
    suspend fun deleteAccount(): okhttp3.ResponseBody

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
        @Part("privacy_agreed") privacyAgreed: RequestBody? = null,
        @Part("avatar_url") avatarUrl: RequestBody? = null
    ): okhttp3.ResponseBody

    @DELETE("profile/photo")
    suspend fun deleteAvatar(): okhttp3.ResponseBody

    @FormUrlEncoded
    @POST("profile/change-password")
    suspend fun changePassword(
        @Field("oldPassword") oldPass: String,
        @Field("newPassword") newPass: String
    ): ResponseBody

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

    // --- ГЛОБАЛЬНЫЙ КОНТЕНТ (ИНФО) ---
    @GET("info")
    suspend fun getGlobalInfo(): GlobalInfoResponse

    @FormUrlEncoded
    @POST("admin/info")
    suspend fun updateGlobalInfo(
        @Field("aboutTitle") aboutTitle: String? = null,
        @Field("aboutDescription") aboutDescription: String? = null,
        @Field("aboutServices") aboutServices: String? = null,
        @Field("aboutFooter") aboutFooter: String? = null,
        @Field("contactTitle") contactTitle: String? = null,
        @Field("contactPhone") contactPhone: String? = null
    ): okhttp3.ResponseBody

    // --- ГЛОБАЛЬНЫЙ КОНТЕНТ (ОФЕРТА) ---
    @GET("policy")
    suspend fun getPrivacyPolicy(): Map<String, String>

    @FormUrlEncoded
    @POST("admin/policy")
    suspend fun updatePrivacyPolicy(
        @Field("date") date: String,
        @Field("content") content: String
    ): okhttp3.ResponseBody

    // --- ТРЕНЕРЫ ---
    @GET("coaches")
    suspend fun getCoaches(@Query("lang") language: String? = null): List<CoachResponse>

    @Multipart
    @POST("admin/coaches")
    suspend fun addCoach(
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @Multipart
    @POST("admin/coaches/{id}")
    suspend fun updateCoach(
        @Path("id") id: String,
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part file: MultipartBody.Part? = null
    ): okhttp3.ResponseBody

    @DELETE("admin/coaches/{id}")
    suspend fun deleteCoach(
        @Path("id") id: String
    ): okhttp3.ResponseBody

    companion object {
        // Базовый адрес по умолчанию (Ваш рабочий домен)
        @Volatile
        private var currentBaseUrl = "https://verso0100.fvds.ru/"
        @Volatile
        private var cachedService: NewsApiService? = null

        fun getBaseUrl(): String = currentBaseUrl

        fun updateBaseUrl(newUrl: String) {
            val normalizedUrl = newUrl.trim().trimEnd('/')
            val formattedUrl = when {
                normalizedUrl.startsWith("https://") -> normalizedUrl
                normalizedUrl.startsWith("http://") -> "https://${normalizedUrl.removePrefix("http://")}"
                else -> "https://$normalizedUrl"
            }
            val finalUrl = "$formattedUrl/"
            
            Log.d("NewsApiService", "Updating Base URL to: $finalUrl")
            
            if (currentBaseUrl != finalUrl) {
                currentBaseUrl = finalUrl
                cachedService = null
                cachedClient = null // Clear client to re-init with new IP if needed
            }
        }

        fun getFullUrl(context: android.content.Context, rawUrl: String?): String {
            if (rawUrl.isNullOrBlank()) return ""
            
            // Preserve HTTPS for URLs returned by the HTTP-only Ktor backend.
            if (rawUrl.startsWith("http")) {
                val normalizedUrl = if (rawUrl.startsWith("http://verso0100.fvds.ru", ignoreCase = true)) {
                    "https://${rawUrl.removePrefix("http://")}"
                } else {
                    rawUrl
                }
                Log.d("NewsApiService", "getFullUrl: URL is already absolute: $normalizedUrl")
                return normalizedUrl
            }
            
            val cleanRaw = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
            val result = currentBaseUrl.removeSuffix("/") + cleanRaw
            
            Log.d("NewsApiService", "getFullUrl: constructed URL: raw=$rawUrl -> result=$result")
            return result
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
                .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val url = request.url.toString()
                    
                    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val token = sharedPref.getString("user_session_token", null)
                    
                    // Добавляем токен для всех запросов к API, включая медиа-файлы,
                    // кроме тех, где он уже есть в заголовках или в URL, или если это гость
                    val isGuestToken = token == "guest_token"
                    val hasAuth = request.header("Authorization") != null || request.url.queryParameter("token") != null
                    val path = request.url.encodedPath
                    val isPublicRequest = (request.method == "GET" && path.endsWith("/news")) ||
                        (request.method == "GET" && path.endsWith("/coaches")) ||
                        (request.method == "POST" && (
                            path.endsWith("/login") ||
                                path.endsWith("/register") ||
                                path.endsWith("/auth/request-otp") ||
                                path.endsWith("/auth/verify-otp") ||
                                path.endsWith("/auth/refresh")
                            ))
                    
                    // Проверяем, является ли запрос запросом к нашему серверу
                    // Мы делаем проверку более гибкой: если это наш IP или если это URL без домена (относительный)
                    val cleanBaseUrl = currentBaseUrl.removePrefix("http://").removePrefix("https://").removeSuffix("/")
                    val isOurServer = url.contains(cleanBaseUrl) || url.contains("5.35.98.149") || !url.startsWith("http")
                    
                    val shouldAddToken = !hasAuth && !isPublicRequest && token != null && !isGuestToken &&
                        (isOurServer || url.contains("admin") || url.contains("chat") || url.contains("cart") || url.contains("orders") || url.contains("profile"))
                    
                    val newRequest = if (shouldAddToken) {
                        Log.d("NewsApiService", "Adding Authorization header to: $url")
                        request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        request
                    }
                    
                    var response = chain.proceed(newRequest)
                    if (response.code == 403 && !hasAuth && token != null &&
                        (path.endsWith("/news") || path.endsWith("/coaches"))) {
                        response.close()
                        response = chain.proceed(request)
                    }
                    Log.d("NewsApiService", "Request: ${request.method} ${request.url} -> Response Code: ${response.code}")
                    if (!response.isSuccessful) {
                        val isOrdersNotFound = url.contains("/orders") && response.code == 404
                        if (isOrdersNotFound) {
                            Log.i("NewsApiService", "Orders endpoint not ready on server, ignoring 404")
                        } else {
                            val errorMsg = response.peekBody(1024).string()
                            val tokenState = when {
                                token == null -> "missing"
                                isGuestToken -> "guest"
                                token.length < 20 -> "invalid_length"
                                else -> "present(length=${token.length})"
                            }
                            Log.e(
                                "NewsApiService",
                                "HTTP_ERROR code=${response.code} method=${request.method} " +
                                    "path=${request.url.encodedPath} authHeader=${hasAuth || shouldAddToken} " +
                                    "token=$tokenState body=${errorMsg.take(1024)}"
                            )
                        }
                    }
                    response
                }
                .authenticator { _, response ->
                    val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    val refreshToken = sharedPref.getString("user_session_refresh_token", null)
                        ?.trim()
                        ?.removePrefix("Bearer ")
                        ?.trim()

                    var responseCount = 1
                    var previousResponse = response.priorResponse
                    while (previousResponse != null) {
                        responseCount++
                        previousResponse = previousResponse.priorResponse
                    }
                    if (response.request.header("Authorization") == null || responseCount >= 2) {
                        return@authenticator null
                    }
                    
                    // Если получили 401 и есть токен обновления
                    if (response.code == 401 && !refreshToken.isNullOrBlank()) {
                        Log.w("NewsApiService", "401 Unauthorized detected. Attempting token refresh...")
                        
                        synchronized(this) {
                            // Повторно читаем токен, возможно другой поток его уже обновил
                            val currentToken = sharedPref.getString("user_session_token", null)
                                ?.trim()
                                ?.removePrefix("Bearer ")
                                ?.trim()
                            val requestToken = response.request.header("Authorization")
                                ?.trim()
                                ?.removePrefix("Bearer ")
                                ?.trim()
                            
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
                                        val newToken = newTokens.token.trim().removePrefix("Bearer ").trim()
                                        val newRefreshToken = newTokens.refreshToken
                                            ?.trim()
                                            ?.removePrefix("Bearer ")
                                            ?.trim()
                                        Log.i("NewsApiService", "Token refreshed successfully!")
                                        sharedPref.edit()
                                            .putString("user_session_token", newToken)
                                            .putString("user_session_refresh_token", newRefreshToken)
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
