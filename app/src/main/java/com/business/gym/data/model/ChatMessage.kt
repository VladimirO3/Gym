package com.business.gym.data.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Timestamp? = null
)
