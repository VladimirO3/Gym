package com.business.gym.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.business.gym.data.model.NewsItem

/**
 * Элемент списка новостей с медиафайлом (фото или видео).
 * Поддерживает удаление администратором.
 */
@Composable
fun NewsMediaItem(item: NewsItem, isAdmin: Boolean, onDelete: () -> Unit) {
    // Считаем текстовой новостью, если URL пустой ИЛИ содержит заглушку /uploads/
    val isTextOnly = item.url.isNullOrBlank() || item.url.endsWith("/uploads/") || item.url == "/uploads"

    // Полностью убираем фон, рамки и тени для всех новостей
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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
                            androidx.compose.material3.Text(
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
                            androidx.compose.material3.Text(
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
                            .height(250.dp)
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
                                    .build(),
                                contentDescription = "News Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
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
