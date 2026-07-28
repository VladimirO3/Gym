package com.business.gym.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.business.gym.data.local.dao.ChatDao
import com.business.gym.data.local.dao.NewsDao
import com.business.gym.data.local.dao.ProfileDao
import com.business.gym.data.local.dao.TrackDao
import com.business.gym.data.local.entity.ChatMessageEntity
import com.business.gym.data.local.entity.NewsEntity
import com.business.gym.data.local.entity.ProfileEntity
import com.business.gym.data.local.entity.TrackEntity
import com.business.gym.data.local.entity.UserEntity

@Database(
    entities = [
        TrackEntity::class, 
        NewsEntity::class, 
        ChatMessageEntity::class, 
        UserEntity::class,
        ProfileEntity::class
    ], 
    version = 4, 
    exportSchema = false
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun newsDao(): NewsDao
    abstract fun chatDao(): ChatDao
    abstract fun profileDao(): ProfileDao

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
