package com.business.gym_app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.business.gym_app.R
import com.business.gym_app.util.PasswordHelper

/**
 * Обязательная смена пароля — полноэкранный блокирующий экран.
 *
 * Показывается вместо основного контента приложения, когда срок пароля
 * (21 день, см. [PasswordHelper]) истёк. Закрыть экран нельзя:
 * без смены пароля пользователь в приложение не попадёт.
 */
@Composable
fun ForcePasswordChangeScreen(
    isLoading: Boolean,
    /** Дней с последней смены — показываем в подзаголовке. */
    daysSinceChange: Long,
    onChangePassword: (oldPass: String, newPass: String, onError: (String) -> Unit) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fillAllFieldsError = stringResource(R.string.fill_all_fields)
    val minLengthError = stringResource(R.string.new_password_min_length)
    val doNotMatchError = stringResource(R.string.passwords_do_not_match)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.password_change_required_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.password_change_required_subtitle,
                daysSinceChange.coerceAtLeast(PasswordHelper.PASSWORD_MAX_AGE_DAYS)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = oldPassword,
            onValueChange = { oldPassword = it; errorMessage = null },
            label = { Text(stringResource(R.string.old_password_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (oldVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (oldVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { oldVisible = !oldVisible }) {
                    Icon(icon, contentDescription = null, tint = Color.Gray)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it; errorMessage = null },
            label = { Text(stringResource(R.string.new_password_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (newVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { newVisible = !newVisible }) {
                    Icon(icon, contentDescription = null, tint = Color.Gray)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = { Text(stringResource(R.string.confirm_new_password_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                errorMessage = null
                if (oldPassword.isBlank() || newPassword.isBlank()) {
                    errorMessage = fillAllFieldsError
                    return@Button
                }
                if (newPassword.length < 6) {
                    errorMessage = minLengthError
                    return@Button
                }
                if (newPassword != confirmPassword) {
                    errorMessage = doNotMatchError
                    return@Button
                }
                onChangePassword(oldPassword, newPassword) { err -> errorMessage = err }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.password_change_save_button),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}