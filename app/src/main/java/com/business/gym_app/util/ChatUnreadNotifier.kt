package com.business.gym_app.util

import android.content.Context
import android.util.Log
import com.business.gym_app.GymApplication
import com.business.gym_app.data.api.NewsApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.UnknownHostException

/**
 * Фоновая проверка непрочитанных сообщений через GET /chat/unread-count.
 *
 * Сервер не отправляет по WebSocket сообщения, пришедшие через HTTP (POST /chat/messages),
 * поэтому при закрытом приложении единственный надежный источник — опрос счетчика.
 * Уже показанные счетчики хранятся в SharedPreferences, чтобы после перезапуска
 * сервиса/воркера не дублировать уведомления.
 */
object ChatUnreadNotifier {
    private const val TAG = "ChatUnreadNotifier"
    private const val PREFS = "chat_unread_notifier"
    private val mutex = Mutex()

    fun sessionToken(context: Context): String? =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .getString("user_session_token", null)
            ?.takeIf { it.isNotBlank() && it != "guest_token" }

    /**
     * Запрашивает счетчики и показывает уведомления по отправителям, у которых
     * число непрочитанных выросло. Пока приложение на экране, уведомления показывает
     * ChatViewModel, здесь только синхронизируются сохраненные счетчики.
     */
    suspend fun check(context: Context) = mutex.withLock {
        val appContext = context.applicationContext
        if (sessionToken(appContext) == null) return@withLock

        val unread = try {
            // DNS-резолвер устройства может быть не готов в первые секунды после старта
            // ("Unable to resolve host ... No address associated with hostname").
            // Повторяем запрос с нарастающей задержкой, чтобы не пропускать проверку.
            withDnsRetry {
                NewsApiService.create(appContext).getUnreadCount()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unread count request failed: ${e.message}")
            return@withLock
        }

        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val silent = GymApplication.isInForeground

        unread.forEach { (senderId, count) ->
            val notified = prefs.getInt(senderId, 0)
            if (count > notified && !silent) {
                NotificationHelper.showNotification(
                    appContext,
                    senderName(senderId),
                    "У вас $count новых сообщений",
                    senderId
                )
            }
            if (count != notified) editor.putInt(senderId, count)
        }

        // Отправители, которых нет в ответе, все прочитали
        prefs.all.keys.filter { it !in unread.keys }.forEach { senderId ->
            editor.remove(senderId)
            NotificationHelper.cancelNotification(appContext, senderId)
        }
        editor.apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Повторяет запрос при DNS-ошибке (домен не резолвится), максимум [maxAttempts] раз
     * с нарастающей задержкой. Остальные ошибки пробрасываются сразу.
     */
    private suspend fun <T> withDnsRetry(
        maxAttempts: Int = 3,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMs = 1_000L
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val isDnsError = e is UnknownHostException ||
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true
                attempt++
                if (!isDnsError || attempt >= maxAttempts) throw e
                Log.d(TAG, "DNS resolution failed (attempt $attempt/$maxAttempts), retrying in ${delayMs}ms")
                delay(delayMs)
                delayMs *= 2
            }
        }
    }

    private fun senderName(senderId: String): String = when {
        AuthUtils.isRootAdmin(senderId) -> "root-администратор"
        senderId == "SYSTEM" -> "СИСТЕМА БЕЗОПАСНОСТИ"
        else -> senderId.substringBefore("@")
    }
}
