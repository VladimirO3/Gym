package com.business.gym.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.business.gym.data.model.NewsItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Элемент списка новостей с медиафайлом (фото или видео).
 * Поддерживает удаление администратором и реакции.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsMediaItem(
    item: NewsItem, 
    isAdmin: Boolean, 
    isGuest: Boolean = false,
    onDelete: () -> Unit,
    onReact: (String) -> Unit = {},
    onClick: () -> Unit = {}
) {
    // Считаем текстовой новостью, если URL пустой ИЛИ содержит заглушку /uploads/
    val isTextOnly = item.url.isNullOrBlank() || item.url.endsWith("/uploads/") || item.url == "/uploads"
    
    val dateText = remember(item.timestamp) {
        if (item.timestamp > 0) {
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
            sdf.format(Date(item.timestamp))
        } else ""
    }

    // Список доступных реакций (Emoji -> Ключ для сервера)
    val reactionList = listOf(
        "🔥" to "fire", 
        "❤️" to "heart", 
        "💪" to "muscle", 
        "👍" to "thumb", 
        "😮" to "wow"
    )

    // Полностью убираем фон, рамки и тени для всех новостей
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent 
        ),
        border = null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Блок текста (СВЕРХУ) - ВСЕГДА ОТОБРАЖАЕМ
                if (!item.title.isNullOrBlank() || !item.content.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isTextOnly) 0.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!item.title.isNullOrBlank()) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.Red,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (!item.content.isNullOrBlank()) {
                            if (!item.title.isNullOrBlank()) Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 2. Блок медиа (СНИЗУ)
                if (!isTextOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f) // Адаптивное соотношение сторон вместо фиксированной высоты
                            .padding(horizontal = 4.dp)
                    ) {
                        if (item.type == "video") {
                            VideoPlayer(
                                videoUrl = item.url, 
                                modifier = Modifier.fillMaxSize(),
                                autoPlay = true,
                                muted = true,
                                looping = true
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.url)
                                    .crossfade(true)
                                    .allowHardware(true)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = "News Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                
                // 3. Блок даты и реакций
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Дата
                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Реакции (для всех, кроме гостей)
                    if (!isGuest) {
                        val userReactionKey = item.userReaction
                        val hasUserReacted = !userReactionKey.isNullOrBlank()

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp), // Эмодзи отображаются вплотную друг к другу
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!hasUserReacted) {
                                // Если пользователь еще не отреагировал — показываем весь список эмодзи для быстрого выбора одним кликом
                                reactionList.forEach { (emoji, key) ->
                                    val count = item.reactions[key] ?: 0
                                    Surface(
                                        color = Color.Transparent, // Кнопки без фона для чистого вида
                                        modifier = Modifier.clickable { onReact(key) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = emoji, fontSize = 16.sp)
                                            if (count > 0) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = count.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Если пользователь отреагировал — сворачиваем список и показываем только активные реакции (счетчик > 0)
                                // или ту реакцию, которую выбрал сам текущий пользователь.
                                reactionList.forEach { (emoji, key) ->
                                    val count = item.reactions[key] ?: 0
                                    val isUserSelected = (key == userReactionKey)

                                    if (count > 0 || isUserSelected) {
                                        Surface(
                                            color = Color.Transparent,
                                            modifier = Modifier.clickable { onReact(key) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = emoji, fontSize = 16.sp)
                                                if (count > 0) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = count.toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        // Подсвечиваем красным только цифру счетчика выбранной реакции
                                                        color = if (isUserSelected) Color.Red else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isUserSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Кнопка удаления для администратора
            if (isAdmin) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = Color.Black.copy(alpha = 0.4f), 
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete, 
                        contentDescription = "Delete Content",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
