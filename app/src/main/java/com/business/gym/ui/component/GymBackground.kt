package com.business.gym.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Универсальный компонент фона для всего приложения.
 * Адаптируется под выбранную в приложении тему (не только системную).
 */
@Composable
fun GymBackground(isDark: Boolean, content: @Composable () -> Unit) {
    // Определение цветов градиента в зависимости от переданного флага темы
    val gradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A0000), // Темно-красный сверху
                Color(0xFF000000), // Черный в середине
                Color(0xFF2B0000)  // Темно-красный снизу
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFEBEE), // Нежно-розовый (светлый красный)
                Color(0xFFFFFFFF), // Белый
                Color(0xFFFFEBEE)
            )
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        content()
    }
}
