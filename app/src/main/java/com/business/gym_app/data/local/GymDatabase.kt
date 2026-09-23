package com.business.gym_app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.business.gym_app.data.local.dao.*
import com.business.gym_app.data.local.entity.*

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
        OrderEntity::class,
        TrainingProgramEntity::class
    ],
    version = 23,
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
    abstract fun trainingProgramDao(): TrainingProgramDao

    companion object {
        // Добавление таблицы программ без очистки остальных данных (иначе сработает destructive-миграция)
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `training_programs` (" +
                        "`id` TEXT NOT NULL, `serverId` INTEGER, `title` TEXT NOT NULL, `coverUrl` TEXT, " +
                        "`exercisesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: GymDatabase? = null

        fun getDatabase(context: Context): GymDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymDatabase::class.java,
                    "gym_database"
                )
                .addMigrations(MIGRATION_22_23)
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
