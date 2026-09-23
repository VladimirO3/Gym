package com.business.gym_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.business.gym_app.data.local.entity.TrainingProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingProgramDao {
    @Query("SELECT * FROM training_programs ORDER BY createdAt")
    fun getAll(): Flow<List<TrainingProgramEntity>>

    @Query("SELECT * FROM training_programs ORDER BY createdAt")
    suspend fun getAllOnce(): List<TrainingProgramEntity>

    @Query("SELECT * FROM training_programs WHERE id = :id")
    suspend fun getById(id: String): TrainingProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(program: TrainingProgramEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programs: List<TrainingProgramEntity>)

    @Query("DELETE FROM training_programs WHERE id = :id")
    suspend fun delete(id: String)
}
