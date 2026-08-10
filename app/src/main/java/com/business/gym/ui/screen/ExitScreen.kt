package com.business.gym.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Прощальный экран (Exit Screen).
 * Вызывается перед полным закрытием приложения для создания эффекта завершенности.
 */
@Composable
fun ExitScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    // Анимация пульсации текста (увеличение и уменьшение)
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.5f else 1f,
        animationSpec = repeatable(
            iterations = 3, // 3 цикла пульсации
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Анимация появления текста
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    // Таймер задержки перед финальным выходом
    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(3000) // Ждем 3 секунды
        onFinished() // Выполняем закрытие Activity
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp) // Увеличиваем отступы
        ) {
            // Крупный агрессивный заголовок
            Text(
                text = "GET BACK\nSOON!", // Переносим на две строки для узких экранов
                color = Color.Red,
                fontSize = 32.sp, // Уменьшаем базовый размер (32 * 1.5 = 48sp макс)
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                modifier = Modifier.scale(scaleAnim).alpha(alphaAnim)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Вторичный текст
            Text(
                text = "THE GAINS DON'T WAIT.",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(alphaAnim)
            )
        }
    }
}
