package com.business.gym.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
        CartItemEntity::class,
        CoachEntity::class,
        GlobalInfoEntity::class,
        OrderEntity::class
    ], 
    version = 22,
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
    abstract fun coachDao(): CoachDao
    abstract fun globalInfoDao(): GlobalInfoDao
    abstract fun orderDao(): OrderDao

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
                // 1. Включаем Write-Ahead Logging (WAL) для повышения отказоустойчивости.
                // Это позволяет БД записывать изменения в отдельный журнал перед внесением в основной файл,
                // что предотвращает повреждение данных при внезапном выключении или сбое.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // 2. Добавляем журналирование транзакций через callback для отладки
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Log.d("GymDatabase", "Database opened in WAL mode. Transaction logging active.")
                    }
                })
                // 3. Журналирование SQL запросов (только для записи/изменения) для анализа сбоев
                .setQueryCallback({ sqlQuery, bindArgs ->
                    if (sqlQuery.contains("INSERT", true) || 
                        sqlQuery.contains("UPDATE", true) || 
                        sqlQuery.contains("DELETE", true)) {
                        Log.i("GymDatabase-Journal", "Transaction: $sqlQuery | Args: $bindArgs")
                    }
                }, { it.run() })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
