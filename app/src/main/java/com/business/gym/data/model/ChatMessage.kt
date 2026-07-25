package com.business.gym.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import androidx.annotation.Keep

/**
 * Модель данных сообщения чата.
 *
 * @property id Уникальный ID документа в Firestore.
 * @property text Текст сообщения (в БД хранится зашифрованным).
 * @property senderId UID пользователя, отправившего сообщение.
 * @property senderName Имя отправителя для отображения над пузырьком сообщения.
 * @property timestamp Время сервера Firebase при создании сообщения.
 */
@Keep
@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Timestamp? = null
)
