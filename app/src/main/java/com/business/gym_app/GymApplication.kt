package com.business.gym_app

import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.receiver.ChatAlarmReceiver
import com.business.gym_app.service.ChatCheckWorker
import com.business.gym_app.util.AppLanguage
import com.business.gym_app.util.ChatUnreadNotifier

/**
 * Базовый класс приложения. 
 */
class GymApplication : Application(), ImageLoaderFactory {
    companion object {
        private var _instance: GymApplication? = null
        val instance: GymApplication
            get() = _instance ?: throw IllegalStateException("GymApplication not initialized")

        private var startedActivities = 0

        /** true, пока хотя бы одна Activity приложения видна пользователю. */
        val isInForeground: Boolean
            get() = startedActivities > 0
    }

    override fun attachBaseContext(base: Context) {
        // Применяем выбранный язык ко всему процессу. AppCompatDelegate.setApplicationLocales()
        // локализует только Activity, а уведомления чата создаются из сервиса/воркера,
        // которые работают с applicationContext.
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        Log.d("GymApplication", "onCreate started")

        // Каналы уведомлений создаем первыми: процесс может быть поднят воркером или
        // receiver'ом без Activity, и уведомление в несуществующий канал отбрасывается.
        com.business.gym_app.util.NotificationHelper.ensureChannels(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { startedActivities++ }
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        // Резервная периодическая проверка сообщений на случай, если система остановила сервис чата
        if (ChatUnreadNotifier.sessionToken(this) != null) {
            ChatCheckWorker.schedule(this)
            ChatAlarmReceiver.schedule(this)
        }

        // Инициализация базового URL из настроек при запуске
        val globalPref = getSharedPreferences("settings_global", MODE_PRIVATE)
        var savedIp = globalPref.getString("server_ip", "https://verso0100.fvds.ru/") ?: "https://verso0100.fvds.ru/"
        if (savedIp.contains("5.35.98.149", ignoreCase = true) || savedIp.startsWith("http://")) {
            savedIp = "https://verso0100.fvds.ru/"
            globalPref.edit().putString("server_ip", savedIp).apply()
        }

        NewsApiService.updateBaseUrl(savedIp)
        
        // Глобальный перехватчик ошибок для отладки вылетов при запуске
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isSystemDeathNotificationException(throwable)) {
                Log.w("GymApplication", "Ignored system AppOps death notification SecurityException in thread ${thread.name}", throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            Log.e("GymApplication", "CRITICAL CRASH in thread ${thread.name}", throwable)
            // Даем время логу записаться
            try { Thread.sleep(2000) } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun isSystemDeathNotificationException(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is SecurityException) {
                val msg = current.message ?: ""
                if (msg.contains("com.google.android.googlequicksearchbox") ||
                    msg.contains("under uid 1000") ||
                    msg.contains("stopWatchingAsyncNoted") ||
                    msg.contains("verifyAndGetBypass")
                ) {
                    return true
                }
            }
            val hasAppOpsInStack = current.stackTrace.any { element ->
                element.className.contains("AppOpsService") ||
                element.methodName.contains("stopWatchingAsyncNoted") ||
                element.methodName.contains("verifyAndGetBypass")
            }
            if (hasAppOpsInStack) {
                return true
            }
            current = current.cause
        }
        return false
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { NewsApiService.getOkHttpClient(this) }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB cache
                    .build()
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of app memory
                    .build()
            }
            .respectCacheHeaders(false) // Игнорируем заголовки сервера для агрессивного кэширования
            .crossfade(true)
            .build()
    }
}
