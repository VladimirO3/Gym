package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coaches")
data class CoachEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val imageUrl: String? = null
)
