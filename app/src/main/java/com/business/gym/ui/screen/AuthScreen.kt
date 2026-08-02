package com.business.gym.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    onAuthSuccess: (String) -> Unit
) {
    val error by viewModel.error
    val isLoading by viewModel.isLoading
    val otpEmail by viewModel.otpEmail
    val otpCode by viewModel.otpCode

    val context = LocalContext.current

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
            text = "Gym App",
            style = MaterialTheme.typography.displaySmall,
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Основное поле для ввода почты
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = { viewModel.requestOtp() },
                enabled = !isLoading && otpEmail.isNotBlank()
            ) {
                Text("Получить код", color = Color.Red)
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
                    viewModel.verifyOtp(context) { onAuthSuccess(otpEmail) }
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
        }
    }
}
