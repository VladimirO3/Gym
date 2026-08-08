package com.business.gym.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.ui.viewmodel.AuthViewModel
import com.business.gym.ui.viewmodel.SettingsViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    settingsViewModel: SettingsViewModel? = null,
    onAuthSuccess: (String) -> Unit
) {
    val error by viewModel.error
    val isLoading by viewModel.isLoading
    val otpEmail by viewModel.otpEmail
    val otpPhone by viewModel.otpPhone
    val otpCode by viewModel.otpCode
    val authMode by viewModel.authMode
    val isPasswordMode by viewModel.isPasswordMode
    val password by viewModel.password

    val context = LocalContext.current
    var showIpDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    // ... (диалог остается без изменений)

    if (showIpDialog && settingsViewModel != null) {
        var ipInput by remember { mutableStateOf(settingsViewModel.serverIp.value) }
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Настройки сервера") },
            text = {
                Column {
                    Text("Введите IP-адрес и порт локального сервера (например, 10.0.2.2:5557 для эмулятора)", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("IP:Порт") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    settingsViewModel.setServerIp(context, null, ipInput)
                    showIpDialog = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "GYM ABS",
                style = MaterialTheme.typography.displaySmall,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Переключатель Email / Телефон
            TabRow(
                selectedTabIndex = if (authMode == "email") 0 else 1,
                containerColor = Color.Transparent,
                contentColor = Color.Red,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (authMode == "email") 0 else 1]),
                        color = Color.Red
                    )
                }
            ) {
                Tab(
                    selected = authMode == "email",
                    onClick = { viewModel.setAuthMode("email") },
                    text = { Text("Email", color = if (authMode == "email") Color.Red else Color.Gray) }
                )
                Tab(
                    selected = authMode == "phone",
                    onClick = { viewModel.setAuthMode("phone") },
                    text = { Text("Телефон", color = if (authMode == "phone") Color.Red else Color.Gray) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (authMode == "email") {
                OutlinedTextField(
                    value = otpEmail,
                    onValueChange = { viewModel.onOtpEmailChange(it) },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )

                if (isPasswordMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = { Text("Пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Color.Red,
                            focusedBorderColor = Color.Red
                        )
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { viewModel.onOtpCodeChange(it) },
                        label = { Text("Код подтверждения (OTP)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Color.Red,
                            focusedBorderColor = Color.Red
                        )
                    )
                }
            } else {
                OutlinedTextField(
                    value = otpPhone,
                    onValueChange = { viewModel.onOtpPhoneChange(it) },
                    label = { Text("Номер телефона (например, +7...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { viewModel.onOtpCodeChange(it) },
                    label = { Text("Код подтверждения (OTP)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (authMode == "email") {
                    TextButton(onClick = { viewModel.togglePasswordMode() }) {
                        Text(if (isPasswordMode) "Использовать OTP" else "Войти по паролю", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (!isPasswordMode) {
                    TextButton(
                        onClick = { viewModel.requestOtp() },
                        enabled = !isLoading && (if (authMode == "email") otpEmail.isNotBlank() else otpPhone.isNotBlank())
                    ) {
                        Text("Получить код", color = Color.Red)
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = if (error!!.contains("отправлен") || error!!.contains("успешно")) Color.Green else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.Red)
            } else {
                Button(
                    onClick = {
                        if (isPasswordMode) {
                            viewModel.signInWithEmail { onAuthSuccess(it) }
                        } else {
                            viewModel.verifyOtp(context) { onAuthSuccess(it) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(
                        "ВОЙТИ / ЗАРЕГИСТРИРОВАТЬСЯ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { 
                        viewModel.loginAsGuest { onAuthSuccess(AuthViewModel.GUEST_EMAIL) }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                ) {
                    Text(
                        "ВОЙТИ КАК ГОСТЬ", 
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Кнопка настроек IP в углу
        if (settingsViewModel != null) {
            IconButton(
                onClick = { showIpDialog = true },
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Server Settings", tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
    }
}

