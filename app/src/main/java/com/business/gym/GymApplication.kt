package com.business.gym

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
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
