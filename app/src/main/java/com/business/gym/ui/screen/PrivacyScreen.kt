package com.business.gym.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.SettingsViewModel
import androidx.compose.runtime.*

/**
 * Экран политики конфиденциальности и правовой информации.
 * @param onAgree лямбда для сохранения согласия
 * @param isAlreadyAgreed флаг, если пользователь уже нажимал "согласен"
 * @param isAdmin флаг администратора для редактирования контента
 */
@Composable
fun PrivacyScreen(
    onAgree: () -> Unit,
    isAlreadyAgreed: Boolean = false,
    isAdmin: Boolean = false,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val horizontalPadding = if (isWideScreen) 64.dp else 16.dp

    val serverContent by viewModel.privacyPolicyText
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchPrivacyPolicy()
    }

    LaunchedEffect(serverContent) {
        if (editText.isBlank() && serverContent.isNotBlank()) {
            editText = serverContent
        }
    }

    val displayContent = if (serverContent.isNotBlank()) serverContent else stringResource(R.string.privacy_content)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(40.dp)) // Placeholder to balance
            Text(
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 24.dp).weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            if (isAdmin) {
                IconButton(onClick = { 
                    if (isEditing) {
                        viewModel.updatePrivacyPolicy(editText) { isEditing = false }
                    } else {
                        editText = displayContent
                        isEditing = true 
                    }
                }) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.Red
                    )
                }
            } else {
                Spacer(Modifier.width(40.dp))
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                ),
                label = { Text("Текст Оферты (Markdown/Plain)", color = Color.Gray) }
            )
            
            Row(modifier = Modifier.padding(top = 16.dp)) {
                TextButton(onClick = { isEditing = false }) {
                    Text("Отмена", color = Color.Gray)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
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

