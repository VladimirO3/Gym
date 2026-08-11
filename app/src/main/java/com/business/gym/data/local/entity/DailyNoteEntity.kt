package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_notes")
data class DailyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uid: String,
    val date: String, // Format: YYYY-MM-DD
    val note: String
)
