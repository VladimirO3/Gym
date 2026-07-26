package com.business.gym.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.business.gym.R
import kotlinx.coroutines.delay

/**
 * Экран заставки (Splash Screen), который видит пользователь при запуске.
 * Создает первое впечатление агрессивного спортивного стиля.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Анимация увеличения логотипа ("эффект пружины")
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Анимация прозрачности текста
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    // Запуск анимации и таймер закрытия заставки
    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(3000) // Показываем экран 3 секунды
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Логотип с анимацией масштаба
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_background),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(180.dp)
                    .scale(scaleAnim)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Название зала в агрессивном стиле
            Text(
                text = "ABS GYM",
                color = Color.Red,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.scale(alphaAnim)
            )
            
            // Мотивирующий лозунг
            Text(
                text = "WORK HARD OR GO HOME!",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 16.dp).scale(alphaAnim)
            )
        }
        
        // Подпись внизу экрана
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "NO PAIN NO GAIN",
                color = Color.DarkGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}
