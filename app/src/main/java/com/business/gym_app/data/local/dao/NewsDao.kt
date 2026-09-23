package com.business.gym_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.business.gym_app.data.local.entity.NewsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM news")
    fun getAllNews(): Flow<List<NewsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(news: List<NewsEntity>)

    @Query("DELETE FROM news")
    suspend fun deleteAll()

    /**
     * Атомарная замена кэша: Flow не увидит промежуточный пустой список,
     * поэтому лента не сбрасывает позицию прокрутки.
     */
    @Transaction
    suspend fun replaceAll(news: List<NewsEntity>) {
        deleteAll()
        insertAll(news)
    }
}
