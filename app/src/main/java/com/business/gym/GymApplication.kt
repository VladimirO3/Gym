package com.business.gym

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.business.gym.data.api.NewsApiService

/**
 * Базовый класс приложения. 
 */
class GymApplication : Application(), ImageLoaderFactory {
    companion object {
        private var _instance: GymApplication? = null
        val instance: GymApplication
            get() = _instance ?: throw IllegalStateException("GymApplication not initialized")
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        android.util.Log.d("GymApplication", "onCreate started")
        
        // Глобальный перехватчик ошибок для отладки вылетов при запуске
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("GymApplication", "CRITICAL CRASH in thread ${thread.name}", throwable)
            // Даем время логу записаться
            try { Thread.sleep(2000) } catch (e: Exception) {}
            // Мы не выходим здесь, чтобы системный обработчик тоже мог сработать
        }
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
