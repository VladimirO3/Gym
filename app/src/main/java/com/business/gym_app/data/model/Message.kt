package com.business.gym_app.data.model

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0
)
