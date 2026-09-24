package com.business.gym_app.util

import android.content.Context
import android.util.Log
import com.business.gym_app.R
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

    /**
     * Идентификаторы (uid и email) собеседника, чей диалог сейчас открыт на экране.
     * Уведомление не показывается только для открытого диалога — остальные
     * показываются всегда, в том числе когда приложение открыто на другой вкладке.
     *
     * Раньше проверки молчали, пока приложение было на переднем плане
     * (GymApplication.isInForeground), но счетчики все равно записывались как
     * «уже уведомленные». Если ChatViewModel в этот момент уведомление не показывал
     * (splash, другая вкладка, пересоздание Activity, гость без токена),
     * сообщение навсегда оставалось без уведомления.
     */
    @Volatile
    var activeChatKeys: Set<String> = emptySet()

    fun sessionToken(context: Context): String? =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .getString("user_session_token", null)
            ?.takeIf { it.isNotBlank() && it != "guest_token" }

    /**
     * Запрашивает счетчики и показывает уведомления по отправителям, у которых
     * число непрочитанных выросло.
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
        val openChat = activeChatKeys
        // Текст уведомления берется из ресурсов в локали приложения: опрос может
        // выполняться сервисом/воркером, чей контекст не обновляется вместе с Activity.
        val res = AppLanguage.localized(appContext).resources

        unread.forEach { (senderId, count) ->
            val notified = prefs.getInt(senderId, 0)
            if (count > notified) {
                if (senderId in openChat) {
                    Log.d(TAG, "Notification suppressed: chat with $senderId is open on screen")
                } else {
                    NotificationHelper.showNotification(
                        appContext,
                        senderName(res, senderId),
                        res.getString(R.string.new_messages_count, count),
                        senderId
                    )
                    NotificationHelper.setBadge(appContext, unread.values.sum())
                }
            }
            if (count != notified) editor.putInt(senderId, count)
        }

        // Отправители, которых нет в ответе, все прочитали
        prefs.all.keys.filter { it !in unread.keys }.forEach { senderId ->
            editor.remove(senderId)
            NotificationHelper.cancelNotification(appContext, senderId)
        }
        if (unread.isEmpty()) NotificationHelper.setBadge(appContext, 0)
        editor.apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Сохраненные счетчики «о ком уже уведомил». Общая точка правды для фонового
     * опроса (сервис/alarm/воркер) и для ChatViewModel: без нее при каждом запуске
     * приложения уведомления о старых непрочитанных показывались заново.
     */
    fun savedCounts(context: Context): Map<String, Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }
            .toMap()

    /** Помечает отправителя уведомленным (счетчик [count] уже показан пользователю). */
    fun markNotified(context: Context, senderId: String, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(senderId, count)
            .apply()
    }

    /** Снимает отметку «уведомлен» (диалог прочитан). */
    fun forgetNotified(context: Context, senderId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(senderId)
            .apply()
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

    private fun senderName(res: android.content.res.Resources, senderId: String): String = when {
        AuthUtils.isRootAdmin(senderId) -> res.getString(R.string.root_administrator)
        senderId == "SYSTEM" -> res.getString(R.string.system_security)
        else -> senderId.substringBefore("@")
    }
}
