package com.business.gym_app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.business.gym_app.service.ChatCheckWorker
import com.business.gym_app.service.ChatForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("user_session_token", null)
            if (!token.isNullOrBlank() && token != "guest_token") {
                ChatAlarmReceiver.schedule(context)
                // Резервная проверка: на Android 12+ старт foreground-сервиса из
                // BOOT_COMPLETED запрещен, и без воркера уведомления не придут вовсе.
                ChatCheckWorker.schedule(context)
                val serviceIntent = Intent(context, ChatForegroundService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start ChatForegroundService on boot", e)
                }
            }
        }
    }
}
