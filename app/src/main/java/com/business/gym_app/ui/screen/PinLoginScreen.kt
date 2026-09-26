package com.business.gym_app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.business.gym_app.R
import com.business.gym_app.util.PinHelper

/**
 * Отдельный экран входа по PIN-коду из 4 цифр.
 *
 * Вместо обычного текстового поля — крупные красные кнопки-цифры,
 * кнопка удаления последней цифры и кнопка отпечатка (вход по биометрии).
 * Вход происходит автоматически после 4-й цифры или по кнопке отпечатка.
 */
@Composable
fun PinLoginScreen(
    pinValue: String,
    error: String?,
    isLoading: Boolean,
    /** На устройстве доступна биометрия и она разрешена в профиле. */
    biometricAvailable: Boolean,
    onPinChange: (String) -> Unit,
    /** Вызывается автоматически, как только введены 4 цифры. */
    onSubmit: () -> Unit,
    onBiometric: () -> Unit,
    /** «Забыли PIN?» — вернуться к входу по паролю. */
    onUsePassword: () -> Unit
) {
    val deleteDesc = stringResource(R.string.pin_delete_digit)
    val biometricDesc = stringResource(R.string.biometric_login_button)
    val forgotText = stringResource(R.string.pin_forgot)
    val enterText = stringResource(R.string.pin_enter)

    // Введены 4 цифры — сразу пробуем войти.
    LaunchedEffect(pinValue) {
        if (pinValue.length == PinHelper.PIN_LENGTH && !isLoading) onSubmit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GYM ABS",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = enterText, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        // Индикатор введённых цифр: 4 точки.
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(PinHelper.PIN_LENGTH) { index ->
                val filled = index < pinValue.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (filled) Color.Red else Color.Gray.copy(alpha = 0.45f))
                )
                if (index < PinHelper.PIN_LENGTH - 1) Spacer(modifier = Modifier.width(14.dp))
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Color.Red)
        } else {
            PinKeypad(
                onDigit = { digit ->
                    if (pinValue.length < PinHelper.PIN_LENGTH) onPinChange(pinValue + digit)
                },
                onDelete = {
                    if (pinValue.isNotEmpty()) onPinChange(pinValue.dropLast(1))
                },
                onBiometric = onBiometric,
                biometricAvailable = biometricAvailable,
                deleteDesc = deleteDesc,
                biometricDesc = biometricDesc
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onUsePassword) {
            Text(forgotText, color = Color.Gray)
        }
    }
}

/**
 * Клавиатура PIN: три колонки крупных красных кнопок-цифр.
 * В нижнем ряду — отпечаток, «0» и удаление цифры.
 */
@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
    biometricAvailable: Boolean,
    deleteDesc: String,
    biometricDesc: String
) {
    val buttonSize = 76.dp
    val spacing = 16.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.forEach { digit ->
                    PinDigitButton(size = buttonSize, onClick = { onDigit(digit) }) {
                        Text(
                            text = digit,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing))
                }
            }
            Spacer(modifier = Modifier.height(spacing))
        }

        // Нижний ряд: отпечаток / 0 / удаление.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (biometricAvailable) {
                PinDigitButton(size = buttonSize, onClick = onBiometric) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = biometricDesc,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            } else {
                // Место под отпечаток оставляем, чтобы цифры не «прыгали».
                Spacer(modifier = Modifier.size(buttonSize))
            }
            Spacer(modifier = Modifier.width(spacing))

            PinDigitButton(size = buttonSize, onClick = { onDigit("0") }) {
                Text(
                    text = "0",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(spacing))

            PinDigitButton(size = buttonSize, onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = deleteDesc,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/** Крупная красная круглая кнопка клавиатуры PIN с центрированным содержимым. */
@Composable
private fun PinDigitButton(
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Red)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}