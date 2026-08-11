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
import coil.compose.AsyncImage
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.model.ChatMessage
import com.business.gym.ui.viewmodel.AuthViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*

/**
 * Отрисовка пузырька сообщения с выравниванием и подписями.
 * Администратор — слева, Пользователь — справа.
 */
@Composable
fun MessageBubble(message: ChatMessage, currentUid: String, currentUserEmail: String?) {
    val isMe = message.senderId == currentUid || (currentUserEmail != null && message.senderId == currentUserEmail)
    val isSenderAdmin = AuthViewModel.isStaticAdmin(message.senderId)
    
    // Выравнивание: Мои сообщения СПРАВА, чужие СЛЕВА
    // Если я Админ, мои сообщения справа. Если я Пользователь, мои сообщения справа.
    val alignment = if (isMe) Alignment.End else Alignment.Start
    
    // Цвета: Мои - Красный/Темный, Чужие - Серый/Темный
    val bubbleColor = if (isMe) {
        Color.Red.copy(alpha = 0.8f)
    } else {
        Color.DarkGray.copy(alpha = 0.8f)
    }

    val label = if (isSenderAdmin) "Администратор" else "Пользователь"
    // Подсветка имени: красный для админа, голубой для пользователя
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
                start = if (alignment == Alignment.Start) 8.dp else 0.dp, 
                end = if (alignment == Alignment.End) 8.dp else 0.dp, 
                bottom = 2.dp
            )
        )
        
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (alignment == Alignment.Start) 0.dp else 16.dp,
                bottomEnd = if (alignment == Alignment.End) 0.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            val context = LocalContext.current
            var showFullMedia by remember { mutableStateOf(false) }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Если есть медиа-файл
                if (!message.mediaUrl.isNullOrBlank()) {
                    val fullMediaUrl = NewsApiService.getFullUrl(context, message.mediaUrl)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .clickable { showFullMedia = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (message.mediaType == "video") {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        } else {
                            AsyncImage(
                                model = fullMediaUrl,
                                contentDescription = "Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (showFullMedia) {
                        Dialog(
                            onDismissRequest = { showFullMedia = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                if (message.mediaType == "video") {
                                    VideoPlayer(
                                        videoUrl = message.mediaUrl,
                                        modifier = Modifier.fillMaxSize(),
                                        autoPlay = true
                                    )
                                } else {
                                    AsyncImage(
                                        model = fullMediaUrl,
                                        contentDescription = "Full Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                IconButton(
                                    onClick = { showFullMedia = false },
                                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White)
                                }
                            }
                        }
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                
                // Иконка прочтения ВСЕГДА для моих сообщений
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
