package com.business.gym_app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.business.gym_app.util.ChatUnreadNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Периодическая проверка сообщений через AlarmManager при закрытом приложении.
 * Работает стабильно на всех версиях Android (включая 12+ / 14 / 15), так как
 * BroadcastReceiver может совершать быстрый сетевой запрос без ограничений Foreground Service.
 */
class ChatAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        // Планируем следующий запуск через 1 минуту
        schedule(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ChatUnreadNotifier.sessionToken(appContext) != null) {
                    ChatUnreadNotifier.check(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking unread messages in ChatAlarmReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ChatAlarmReceiver"
        private const val REQUEST_CODE = 1002

        fun schedule(context: Context) {
            if (ChatUnreadNotifier.sessionToken(context) == null) return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ChatAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMs = System.currentTimeMillis() + 60_000L // Каждую 1 минуту
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarm", e)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ChatAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
