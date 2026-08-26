package com.business.gym.data.model

import androidx.annotation.Keep

/**
 * Модель данных сообщения чата.
 */
@Keep
data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)
