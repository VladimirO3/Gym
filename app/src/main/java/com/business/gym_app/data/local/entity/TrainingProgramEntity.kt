package com.business.gym_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальная копия программы тренировок из таблицы training_programs на VPS.
 */
@Entity(tableName = "training_programs")
data class TrainingProgramEntity(
    @PrimaryKey val id: String,          // DailyWorkout.id (client_id на сервере)
    val serverId: Int? = null,           // training_programs.id; null — программа еще не на сервере
    val title: String,
    val coverUrl: String? = null,
    val exercisesJson: String,           // JSON-список Exercise
    val createdAt: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = false     // есть локальные изменения, которые не дошли до сервера
)
