package com.business.gym

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.business.gym.data.api.NewsApiService
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Базовый класс приложения. 
 * Используется для глобальной инициализации настроек Firebase до запуска любых экранов.
 */
class GymApplication : Application(), ImageLoaderFactory {
    companion object {
        lateinit var instance: GymApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d("GymApplication", "Application onCreate started")
        
        // Настройка оффлайн-сохранения (Persistence) должна происходить
        // строго ПРИ ЗАПУСКЕ приложения, до любого обращения к данным.
        try {
            configureFirebasePersistence()
            android.util.Log.d("GymApplication", "Firebase Persistence configured")
        } catch (e: Exception) {
            android.util.Log.e("GymApplication", "Failed to configure Firebase", e)
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
        // 1. Настройка Firestore (Оффлайн кэш неограниченного размера)
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        firestore.firestoreSettings = settings

        // 2. Настройка Realtime Database (Включение оффлайн режима)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
