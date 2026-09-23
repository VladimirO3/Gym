package com.business.gym_app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.business.gym_app.R
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.business.gym_app.MainActivity

/**
 * Помощник для отображения системных уведомлений в шторке.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "gym_notifications"
    private const val CHANNEL_NAME = "Gym App Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications for chat messages and errors"

    /**
     * Показывает уведомление.
     */
    fun showNotification(context: Context, title: String, message: String, senderId: String? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создание канала (для Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Проверка разрешений для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Создаем Intent для открытия MainActivity при нажатии
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
            putExtra("sender_id", senderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            senderId?.hashCode() ?: 0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Сборка уведомления
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_background)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Отображение
        NotificationManagerCompat.from(context).notify(senderId?.hashCode() ?: 1, notification)
    }

    /**
     * Удаляет уведомление для конкретного отправителя.
     */
    fun cancelNotification(context: Context, senderId: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(senderId?.hashCode() ?: 1)
    }

    /**
     * Удаляет все уведомления приложения.
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    /**
     * Открывает настройки автозапуска и оптимизации батареи в зависимости от производителя смартфона
     * (Xiaomi/MIUI, Huawei, Samsung, Oppo, Vivo, Realme) или системные настройки приложения.
     */
    fun openAutostartAndBatterySettings(context: Context) {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        val intents = mutableListOf<Intent>()

        if (brand.contains("xiaomi") || manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            intents.add(Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            })
        } else if (brand.contains("huawei") || manufacturer.contains("huawei") || brand.contains("honor")) {
            intents.add(Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            })
            intents.add(Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            })
        } else if (brand.contains("samsung") || manufacturer.contains("samsung")) {
            intents.add(Intent().apply {
                component = ComponentName("com.samsung.android.looper", "com.samsung.android.sm.ui.battery.BatteryActivity")
            })
            intents.add(Intent().apply {
                component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
            })
        } else if (brand.contains("oppo") || brand.contains("realme") || manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            intents.add(Intent().apply {
                component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            })
        } else if (brand.contains("vivo") || manufacturer.contains("vivo")) {
            intents.add(Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            })
        }

        // Резервный переход в системные настройки о приложении
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // пробуем следующий
            }
        }
    }
}
