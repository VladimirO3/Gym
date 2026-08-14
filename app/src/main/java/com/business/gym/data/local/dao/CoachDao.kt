package com.business.gym.data.local.dao

import androidx.room.*
import com.business.gym.data.local.entity.CoachEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachDao {
    @Query("SELECT * FROM coaches")
    fun getAllCoaches(): Flow<List<CoachEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoaches(coaches: List<CoachEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoach(coach: CoachEntity)

    @Delete
    suspend fun deleteCoach(coach: CoachEntity)

    @Query("DELETE FROM coaches")
    suspend fun deleteAllCoaches()
}
