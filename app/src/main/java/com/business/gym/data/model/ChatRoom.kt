package com.business.gym.data.model

import com.google.firebase.Timestamp

data class ChatRoom(
    val id: String = "",
    val name: String = "",
    val lastMessage: String = "",
    val updatedAt: Timestamp? = null
)
