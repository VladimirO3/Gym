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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
	primary = Color.White,
	secondary = Color.White,
	background = Color.Black,
	surface = Color.Black,
	onPrimary = Color.Black,
	onSecondary = Color.Black,
	onBackground = Color.White,
	onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
	primary = Color.Black,
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

	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography,
		content = content
	)
}