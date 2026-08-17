package com.business.gym.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.business.gym.data.model.Track

/**
 * Элемент списка треков. 
 * Адаптируется под темную и светлую темы.
 */
@Composable
fun TrackItem(
    track: Track, 
    isSelected: Boolean, 
    isPlaying: Boolean,
    isAdmin: Boolean, 
    onDelete: () -> Unit, 
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayPause),
        // Подсветка выбранного трека через цвет контейнера и рамку
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.Red) else null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isSelected && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    // Адаптивный цвет иконки: красный для выбора, иначе системный
                    tint = if (isSelected) Color.Red else MaterialTheme.colorScheme.onBackground
                )
            }
            
            if (isSelected) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            
            // Название трека: цвет меняется автоматически (черный в светлой теме, белый в темной)
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) Color.Red else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (isAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete, 
                        contentDescription = "Delete Track", 
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
