package com.business.gym.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.business.gym.R

/**
 * Экран политики конфиденциальности и правовой информации.
 * @param onAgree лямбда для сохранения согласия
 * @param isAlreadyAgreed флаг, если пользователь уже нажимал "согласен"
 */
@Composable
fun PrivacyScreen(onAgree: () -> Unit, isAlreadyAgreed: Boolean = false) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isWideScreen = configuration.screenWidthDp > 600
    val horizontalPadding = if (isWideScreen) 64.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.privacy_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Red, // Яркий красный заголовок
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.privacy_content),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Связь с автором ---
        Text(
            text = "Связаться с автором:",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Telegram
            AuthorContactIcon(
                icon = Icons.AutoMirrored.Filled.Send,
                label = "Telegram",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BBC2331"))
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // GitHub
            AuthorContactIcon(
                icon = Icons.Default.Code,
                label = "GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VladimirO3"))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Email
            AuthorContactIcon(
                icon = Icons.Default.Email,
                label = "Email",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:verso0100@gmail.com")
                    }
                    context.startActivity(intent)
                }
            )
        }

        // Кнопку показываем только если согласие еще НЕ было дано
        if (!isAlreadyAgreed) {
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onAgree,
                modifier = Modifier
                    .fillMaxWidth(if (isWideScreen) 0.5f else 1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.btn_agree),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Вы приняли условия использования",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun AuthorContactIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.Red,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}
