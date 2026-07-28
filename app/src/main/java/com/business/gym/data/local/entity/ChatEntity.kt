package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: Int,
    val text: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val peerUid: String // Для фильтрации истории с конкретным пользователем
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val name: String
)
