package com.business.gym_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.business.gym_app.R
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.receiver.ChatAlarmReceiver
import com.business.gym_app.util.ChatUnreadNotifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Держит соединение с сервером, пока приложение закрыто, и показывает уведомления о сообщениях.
 *
 * Источник уведомлений — опрос GET /chat/unread-count (сервер не пушит по WebSocket
 * сообщения, отправленные через HTTP). Кадр WebSocket лишь ускоряет очередную проверку.
 */
class ChatForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(OkHttp) { install(WebSockets) }
    private var connectionJob: Job? = null
    private var pollingJob: Job? = null
    private val checkRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(NOTIFICATION_ID, foregroundNotification())
        } catch (e: Exception) {
            // Android 12+ запрещает запуск foreground-сервиса из фона; тогда работает ChatCheckWorker
            android.util.Log.e(TAG, "Cannot start foreground service", e)
            stopSelf()
            return
        }
        ChatCheckWorker.schedule(this)
        connect()
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasToken()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (connectionJob?.isActive != true) connect()
        if (pollingJob?.isActive != true) startPolling()
        checkRequests.trySend(Unit)
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && hasToken()) {
                ChatUnreadNotifier.check(this@ChatForegroundService)
                // Ждем интервал или внеочередной запрос (пришел кадр по WebSocket)
                withTimeoutOrNull(POLL_INTERVAL_MS) { checkRequests.receive() }
            }
        }
    }

    private fun connect() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            var retryDelay = 2_000L
            while (isActive && hasToken()) {
                try {
                    val baseUrl = NewsApiService.getBaseUrl()
                    val hostPart = baseUrl.removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore("/")
                    val host = hostPart.substringBefore(":")
                    val port = hostPart.substringAfter(":", "")
                        .toIntOrNull() ?: if (baseUrl.startsWith("https://")) 443 else 80
                    val protocol = if (baseUrl.startsWith("https://")) URLProtocol.WSS else URLProtocol.WS
                    val token = sessionToken() ?: break

                    client.webSocket(
                        method = HttpMethod.Get,
                        host = host,
                        port = port,
                        path = "/chat/chat",
                        request = { url.protocol = protocol; header(HttpHeaders.Authorization, "Bearer $token") }
                    ) {
                        retryDelay = 2_000L
                        for (frame in incoming) {
                            if (frame is Frame.Text) handleIncoming(frame.readText())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "WebSocket connection failed", e)
                }
                if (isActive && hasToken()) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    private fun handleIncoming(raw: String) {
        // Широковещательные события сервера ({"type": "NEWS_UPDATE", ...}) — не сообщения чата.
        // Личное сообщение приходит зашифрованной строкой, поэтому просто проверяем счетчик.
        val type = runCatching { JSONObject(raw).optString("type") }.getOrNull()
        if (!type.isNullOrBlank()) return
        checkRequests.trySend(Unit)
    }

    private fun foregroundNotification(): Notification =
        NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_background)
            .setContentTitle("Gym")
            .setContentText("Чат подключен")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Отдельный тихий канал для постоянного уведомления сервиса.
            // Сами сообщения показываются через NotificationHelper (канал с высоким приоритетом).
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID, "Подключение чата", NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun sessionToken(): String? = ChatUnreadNotifier.sessionToken(this)

    private fun hasToken() = sessionToken() != null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (hasToken()) {
            ChatAlarmReceiver.schedule(applicationContext)
            ChatCheckWorker.schedule(applicationContext)
        }
    }

    /**
     * Android 15+: dataSync-сервис может работать не более 6 часов в сутки.
     * По истечении лимита сервис обязан остановиться, иначе система завершит приложение.
     * Дальше уведомления продолжает показывать ChatCheckWorker.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        android.util.Log.w(TAG, "Foreground service time limit reached, stopping")
        stopSelf()
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        pollingJob?.cancel()
        scope.cancel()
        client.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ChatForegroundService"
        private const val SERVICE_CHANNEL_ID = "gym_chat_service"
        private const val NOTIFICATION_ID = 7001
        private const val POLL_INTERVAL_MS = 20_000L
    }
}
