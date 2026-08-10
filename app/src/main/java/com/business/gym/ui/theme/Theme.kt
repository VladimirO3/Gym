package com.business.gym.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
	primary = Color(0xFFFF0000), // Красный
	secondary = Color(0xFFB71C1C), // Темно-красный
	background = Color.Black,
	surface = Color(0xFF121212),
	onPrimary = Color.White,
	onSecondary = Color.White,
	onBackground = Color.White,
	onSurface = Color.White,
	primaryContainer = Color(0xAA3B0000), // Полупрозрачный темно-красный
	onPrimaryContainer = Color.White,
	surfaceVariant = Color(0x881C1C1C), // Полупрозрачный серый
	onSurfaceVariant = Color.White,
	secondaryContainer = Color(0xAA5D0000) // Еще один вариант красного
)

private val LightColorScheme = lightColorScheme(
	primary = Color(0xFFB71C1C),
	secondary = Color.Black,
	background = Color.White,
	surface = Color.White,
	onPrimary = Color.White,
	onSecondary = Color.White,
	onBackground = Color.Black,
	onSurface = Color.Black
)

@Composable
fun GymTheme(
	darkTheme: Boolean = true,
	// Dynamic color is available on Android 12+
	dynamicColor: Boolean = false, // Set to false to ensure our black theme is used
	content: @Composable () -> Unit
) {
	val colorScheme = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val context = LocalContext.current
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}

		darkTheme -> DarkColorScheme
		else -> LightColorScheme
	}

	val view = LocalView.current
	if (!view.isInEditMode) {
		SideEffect {
			val window = (view.context as Activity).window
			// Устанавливаем светлые иконки (белые) для темной темы и темные для светлой
			WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
		}
	}

	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography,
		content = content
	)
}
