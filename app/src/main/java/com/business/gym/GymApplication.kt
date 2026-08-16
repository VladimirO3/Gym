package com.business.gym

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.business.gym.data.api.NewsApiService
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Базовый класс приложения. 
 */
class GymApplication : Application(), ImageLoaderFactory {
    companion object {
        private var _instance: GymApplication? = null
        val instance: GymApplication
            get() = _instance ?: throw IllegalStateException("GymApplication not initialized")
            
        private var firebasePersistenceConfigured = false
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
        
        try {
            // FirebaseApp обычно инициализируется автоматически через ContentProvider.
            // Ручная инициализация может вызвать проблемы, если google-services.json не найден.
            // Оставляем только настройку персистентности.
            if (!firebasePersistenceConfigured) {
                configureFirebasePersistence()
                firebasePersistenceConfigured = true
            }
        } catch (e: Exception) {
            android.util.Log.e("GymApplication", "Firebase configuration failed", e)
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

    private fun configureFirebasePersistence() {
        try {
            // 1. Настройка Firestore
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            firestore.firestoreSettings = settings

            // 2. Настройка Realtime Database
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            android.util.Log.e("GymApplication", "Persistence setup error", e)
        }
    }
}
