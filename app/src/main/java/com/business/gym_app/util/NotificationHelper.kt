package com.business.gym_app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Помощник для отображения системных уведомлений в шторке.
 */
object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "gym_notifications"
    private const val SERVICE_CHANNEL_ID = "gym_chat_service"
    private const val GROUP_KEY_CHATS = "com.business.gym_app.CHAT_MESSAGES"

    /** ID постоянного уведомления ChatForegroundService — его нельзя перекрывать. */
    const val SERVICE_NOTIFICATION_ID = 7001

    /**
     * Фоновое уточнение переведённого заголовка. Отдельный скоуп: показ уведомления
     * не должен ждать сеть (см. [showNotification]).
     */
    private val translateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Создает каналы уведомлений. Вызывается при старте процесса (GymApplication),
     * потому что фоновый воркер/receiver могут показать уведомление раньше,
     * чем это сделает Activity, — а уведомление в несуществующий канал
     * Android 8+ просто отбрасывает.
     *
     * Важность уже существующего канала система изменить не позволяет: если канал
     * был создан старой сборкой с IMPORTANCE_LOW (или пользователь его понизил),
     * уведомления молча не показываются. В этом случае канал пересоздается.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // Названия каналов берутся из ресурсов, поэтому контекст должен быть локализован:
        // сервис/воркер работают с applicationContext, который не обновляется вместе с Activity.
        val res = AppLanguage.localized(context).resources
        try {
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                Log.w(TAG, "Chat channel importance was ${existing.importance}, recreating channel")
                manager.deleteNotificationChannel(CHANNEL_ID)
            }
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, res.getString(R.string.notifications_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = res.getString(R.string.notifications_channel_desc)
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
            // Канал пересоздается при каждом вызове: имя и описание канала система
            // обновляет, поэтому после смены языка они становятся на новом языке.
            manager.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID, res.getString(R.string.chat_connection_channel), NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = res.getString(R.string.chat_connection_channel_desc)
                    setShowBadge(false)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels", e)
        }
    }

    /** Разрешен ли показ уведомлений системой и пользователем. */
    fun areNotificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Открывает системные настройки уведомлений приложения.
     * Используется, когда POST_NOTIFICATIONS отклонено «навсегда» и системный
     * диалог больше не показывается.
     */
    fun openNotificationSettings(context: Context) {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intents.add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        }
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // пробуем следующий способ
            }
        }
    }

    /**
     * Стабильный положительный ID уведомления для отправителя:
     * отрицательный hashCode система отклоняет, а совпадение с SERVICE_NOTIFICATION_ID
     * перезаписало бы постоянное уведомление сервиса.
     */
    private fun notificationIdFor(senderId: String?): Int {
        val base = senderId?.hashCode()?.let { kotlin.math.abs(it % 1_000_000) } ?: 0
        val id = 1000 + base
        return if (id == SERVICE_NOTIFICATION_ID) id + 1 else id
    }

    /** Число непрочитанных на иконке приложения (лаунчеры Samsung/Xiaomi/Huawei). */
    fun setBadge(context: Context, count: Int) {
        try {
            val badge = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", count.coerceAtLeast(0))
                putExtra("badge_count_package_name", context.packageName)
                putExtra("badge_count_class_name", MainActivity::class.java.name)
            }
            context.sendBroadcast(badge)
        } catch (e: Exception) {
            Log.w(TAG, "Badge update failed: ${e.message}")
        }
    }

    /**
     * Опрос чата идет в двух местах одновременно (ChatViewModel в приложении и
     * ChatForegroundService/ChatAlarmReceiver в фоне, в одном процессе). Без
     * дедупликации одно и то же сообщение показывается дважды.
     */
    private val lastShown = HashMap<String, Pair<String, Long>>()
    private const val DUPLICATE_WINDOW_MS = 60_000L

    private fun isDuplicate(senderId: String?, message: String): Boolean {
        val key = senderId ?: message
        val now = System.currentTimeMillis()
        synchronized(lastShown) {
            val previous = lastShown[key]
            if (previous != null && previous.first == message &&
                now - previous.second < DUPLICATE_WINDOW_MS
            ) {
                return true
            }
            lastShown[key] = message to now
            return false
        }
    }

    /**
     * Показывает уведомление о сообщении.
     *
     * Заголовок (имя отправителя) берётся из кэша перевода мгновенно; сетевой перевод
     * выполняется асинхронно и молча обновляет уже показанное уведомление. Синхронный
     * сетевой вызов здесь недопустим: ChatAlarmReceiver работает через goAsync()
     * с бюджетом ~10 секунд — долгий запрос к translation.googleapis.com удерживал
     * проверку дольше, и фоновые уведомления при закрытом приложении терялись.
     */
    fun showNotification(context: Context, title: String, message: String, senderId: String? = null) {
        val appContext = context.applicationContext
        ensureChannels(appContext)

        // Проверка разрешений для Android 13+
        if (!areNotificationsEnabled(appContext)) {
            Log.w(TAG, "Notification skipped, permission not granted: sender=$senderId")
            return
        }

        // Один и тот же опрос выполняется и в приложении, и в фоновом сервисе —
        // повторяющееся уведомление не показываем.
        if (isDuplicate(senderId, message)) {
            Log.d(TAG, "Duplicate notification skipped: sender=$senderId")
            return
        }

        // Мгновенный перевод из кэша (его заполняет показ имени в UI); без кэша —
        // исходный заголовок, точный перевод придёт асинхронно после показа.
        val target = AppLanguage.current(appContext)
        val cachedTitle = GoogleTranslate.cached(appContext, title, target) ?: title

        // Сборка уведомления — локальная функция: её переиспользует и асинхронное
        // обновление переведённого заголовка ниже.
        fun buildNotification(titleText: String): Notification {
        // Создаем Intent для открытия MainActivity при нажатии
        val intent = Intent(appContext, MainActivity::class.java).apply {
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

        // Сборка уведомления. Маленькая иконка — монохромный вектор: полноцветный растр
        // из mipmap система рисует сплошным квадратом, а часть оболочек такие
        // уведомления не показывает вовсе.
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gym_logo_white)
            .apply {
                runCatching {
                    BitmapFactory.decodeResource(appContext.resources, R.mipmap.ic_launcher_background)
                }.getOrNull()?.let { setLargeIcon(it) }
            }
            .setContentTitle(titleText)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_CHATS)
            .setContentIntent(pendingIntent)
            .build()
        }

        val notification = buildNotification(cachedTitle)

        // Отображение. Исключение здесь не должно обрывать фоновый опрос сообщений,
        // иначе уведомления пропадут до следующего перезапуска приложения.
        val notificationId = notificationIdFor(senderId)
        try {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
            Log.d(TAG, "Notification shown: sender=$senderId id=$notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "notify() failed for sender=$senderId", e)
            return
        }

        // Сетевой перевод — вне критического пути показа (см. KDoc к showNotification):
        // уведомление уже показано, после ответа заголовок молча уточняется.
        if (GoogleTranslate.needsTranslation(title, target)) {
            translateScope.launch {
                updateTranslatedTitle(appContext, senderId, notificationId, title) {
                    buildNotification(it)
                }
            }
        }
    }

    /**
     * Тихое обновление заголовка уже показанного уведомления после прихода перевода.
     * [build] — локальная сборка из [showNotification]; выполняется в [translateScope],
     * поэтому сеть не задерживает показ (BroadcastReceiver с goAsync ограничен ~10 сек).
     */
    private suspend fun updateTranslatedTitle(
        appContext: Context,
        senderId: String?,
        notificationId: Int,
        sourceTitle: String,
        build: (String) -> Notification
    ) {
        val translated = try {
            GoogleTranslate.localizedText(appContext, sourceTitle)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Async title translation failed: ${e.message}")
            return
        }
        if (translated == sourceTitle) return
        // Обновляем только если уведомление ещё в шторке — после тапа оно снято,
        // и повторный notify вернул бы его пользователю.
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val stillShown = manager.activeNotifications?.any { it.id == notificationId } == true
        if (!stillShown || !areNotificationsEnabled(appContext)) return
        try {
            manager.notify(notificationId, build(translated))
            Log.d(TAG, "Notification title translated: sender=$senderId")
        } catch (e: Exception) {
            Log.w(TAG, "Notification title update failed: ${e.message}")
        }
    }

    /**
     * Удаляет уведомление для конкретного отправителя.
     */
    fun cancelNotification(context: Context, senderId: String?) {
        try {
            // Сбрасываем дедупликацию: после прочтения новое сообщение с тем же
            // счетчиком должно снова показать уведомление.
            senderId?.let { synchronized(lastShown) { lastShown.remove(it) } }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            notificationManager.cancel(notificationIdFor(senderId))
        } catch (e: Exception) {
            Log.w(TAG, "cancelNotification failed: ${e.message}")
        }
    }

    /**
     * Удаляет уведомления о сообщениях, не трогая постоянное уведомление сервиса:
     * cancelAll() убирает и его, после чего система может остановить foreground-сервис.
     */
    fun cancelAllNotifications(context: Context) {
        try {
            synchronized(lastShown) { lastShown.clear() }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            notificationManager.activeNotifications?.forEach { statusBarNotification ->
                if (statusBarNotification.id != SERVICE_NOTIFICATION_ID) {
                    notificationManager.cancel(statusBarNotification.id)
                }
            }
            setBadge(context, 0)
        } catch (e: Exception) {
            Log.w(TAG, "cancelAllNotifications failed: ${e.message}")
        }
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
