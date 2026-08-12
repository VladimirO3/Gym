package com.business.gym.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.business.gym.data.local.dao.*
import com.business.gym.data.local.entity.*

@Database(
    entities = [
        TrackEntity::class, 
        NewsEntity::class, 
        ChatMessageEntity::class, 
        UserEntity::class,
        ProfileEntity::class,
        DailyNoteEntity::class,
        ProductEntity::class,
        CartItemEntity::class
    ], 
    version = 13,
    exportSchema = false
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun newsDao(): NewsDao
    abstract fun chatDao(): ChatDao
    abstract fun profileDao(): ProfileDao
    abstract fun dailyNoteDao(): DailyNoteDao
    abstract fun productDao(): ProductDao
    abstract fun cartDao(): CartDao

    companion object {
        @Volatile
        private var INSTANCE: GymDatabase? = null

        fun getDatabase(context: Context): GymDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymDatabase::class.java,
                    "gym_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
