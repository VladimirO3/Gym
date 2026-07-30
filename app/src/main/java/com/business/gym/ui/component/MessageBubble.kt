package com.business.gym.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.business.gym.data.model.ChatMessage
import com.business.gym.ui.viewmodel.AuthViewModel

/**
 * Отрисовка пузырька сообщения с выравниванием.
 * Текст текущего пользователя — справа, собеседника — слева.
 */
@Composable
fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    // Определяем, является ли собеседник администратором для специальной расцветки
    val isPeerAdmin = AuthViewModel.isStaticAdmin(message.senderId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // Отображаем имя отправителя, если это не я
        if (!isMe) {
            val displayName = if (isPeerAdmin) "Администратор" else message.senderName
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPeerAdmin) Color.Red else Color.Gray,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
        
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 16.dp
            ),
            color = if (isMe) Color.Red.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                
                // Дополнительная информация внизу сообщения
                Row(
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Маркер прочтения только для МОИХ сообщений (справа)
                    if (isMe) {
                        Icon(
                            imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null,
                            tint = if (message.isRead) Color.Cyan else Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
