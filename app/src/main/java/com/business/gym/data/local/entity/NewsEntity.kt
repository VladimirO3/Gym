package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val mediaUrl: String,
    val mediaType: String = "image",
    val createdAt: String = "",
    // Реакции
    val fireCount: Int = 0,
    val heartCount: Int = 0,
    val muscleCount: Int = 0,
    val thumbCount: Int = 0,
    val wowCount: Int = 0
)
