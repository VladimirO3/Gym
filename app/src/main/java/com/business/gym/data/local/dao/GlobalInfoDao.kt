package com.business.gym.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.business.gym.data.local.entity.GlobalInfoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalInfoDao {
    @Query("SELECT * FROM global_info WHERE id = 'info'")
    fun getGlobalInfo(): Flow<GlobalInfoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalInfo(info: GlobalInfoEntity)
}
