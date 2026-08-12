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
        lateinit var instance: GymApplication
            private set
            
        private var firebasePersistenceConfigured = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d("GymApplication", "onCreate started")
        
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
            android.util.Log.d("GymApplication", "FirebaseApp initialized")
            
            if (!firebasePersistenceConfigured) {
                configureFirebasePersistence()
                firebasePersistenceConfigured = true
                android.util.Log.d("GymApplication", "Firebase Persistence configured")
            }
        } catch (e: Exception) {
            android.util.Log.e("GymApplication", "Firebase initialization failed", e)
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
