package com.business.gym

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Базовый класс приложения. 
 * Используется для глобальной инициализации настроек Firebase до запуска любых экранов.
 */
class GymApplication : Application() {
    companion object {
        lateinit var instance: GymApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Настройка оффлайн-сохранения (Persistence) должна происходить
        // строго ПРИ ЗАПУСКЕ приложения, до любого обращения к данным.
        configureFirebasePersistence()
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
