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
    val peerUid: String, // Для фильтрации истории с конкретным пользователем
    val isRead: Boolean = false,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val serverId: String? = null,
    val email: String,
    val name: String,
    val age: Int? = null,
    val avatarUrl: String? = null,
    val lastSeen: Long? = null,
    val isAdmin: Boolean = false,
    val role: String? = "user"
)
