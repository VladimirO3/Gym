package com.business.gym.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    onAuthSuccess: (String) -> Unit
) {
    val email by viewModel.email
    val password by viewModel.password
    val confirmPassword by viewModel.confirmPassword
    val isLogin by viewModel.isLogin
    val error by viewModel.error
    val isLoading by viewModel.isLoading

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
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Вкладки: Вход / Регистрация
        TabRow(
            selectedTabIndex = if (isLogin) 0 else 1,
            containerColor = Color.Transparent,
            contentColor = Color.Red,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[if (isLogin) 0 else 1]),
                    color = Color.Red
                )
            },
            divider = {}
        ) {
            Tab(
                selected = isLogin,
                onClick = { viewModel.toggleIsLogin() },
                text = { Text("ВХОД") }
            )
            Tab(
                selected = !isLogin,
                onClick = { viewModel.toggleIsLogin() },
                text = { Text("РЕГИСТРАЦИЯ") }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { viewModel.onEmailChange(it) },
            label = { Text("Email или Логин") },
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
            value = password,
            onValueChange = { viewModel.onPasswordChange(it) },
            label = { Text(stringResource(R.string.auth_password_hint)) },
            visualTransformation = PasswordVisualTransformation(),
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

        if (!isLogin) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { viewModel.onConfirmPasswordChange(it) },
                label = { Text("Повторите пароль") },
                visualTransformation = PasswordVisualTransformation(),
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

        if (error != null) {
            Text(
                text = error!!, 
                color = if (error!!.contains("отправлена") || error!!.contains("ожидает")) Color.Green else MaterialTheme.colorScheme.error, 
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
                    if (isLogin) viewModel.signInWithEmail(onAuthSuccess)
                    else viewModel.signUpWithEmail(onAuthSuccess)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(
                    if (isLogin) stringResource(R.string.auth_login) else "ОТПРАВИТЬ ЗАЯВКУ", 
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
