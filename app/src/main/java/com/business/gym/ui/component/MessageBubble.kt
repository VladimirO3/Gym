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
import com.business.gym.data.model.ChatMessage
import com.business.gym.ui.viewmodel.AuthViewModel

/**
 * Отрисовка пузырька сообщения с выравниванием и подписями.
 * Администратор — слева, Пользователь — справа.
 */
@Composable
fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    val isSenderAdmin = AuthViewModel.isStaticAdmin(message.senderId)
    
    // Выравнивание: Админ СЛЕВА, Пользователь СПРАВА
    val alignment = if (isSenderAdmin) Alignment.Start else Alignment.End
    
    // Цвета: Админ - Серый/Красный, Пользователь - Красный/Синий
    val bubbleColor = if (isSenderAdmin) {
        Color.DarkGray.copy(alpha = 0.8f)
    } else {
        Color.Red.copy(alpha = 0.8f)
    }

    val label = if (isSenderAdmin) "Администратор" else "Пользователь"
    val labelColor = if (isSenderAdmin) Color.Red else Color.Cyan

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        // Подпись роли отправителя
        Text(
            text = if (isMe) "$label (Вы)" else label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isMe) Color.White.copy(alpha = 0.7f) else labelColor,
            modifier = Modifier.padding(
                start = if (isSenderAdmin) 8.dp else 0.dp, 
                end = if (isSenderAdmin) 0.dp else 8.dp, 
                bottom = 2.dp
            )
        )
        
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSenderAdmin) 0.dp else 16.dp,
                bottomEnd = if (isSenderAdmin) 16.dp else 0.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                
                // Иконка прочтения только для своих сообщений
                if (isMe) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
