package com.business.gym.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    onAuthSuccess: (String) -> Unit
) {
    val email by viewModel.email
    val password by viewModel.password
    val confirmPassword by viewModel.confirmPassword
    val phoneNumber by viewModel.phoneNumber
    val otpCode by viewModel.otpCode
    val verificationId by viewModel.verificationId
    val authMode by viewModel.authMode
    val isLogin by viewModel.isLogin
    val error by viewModel.error
    val isLoading by viewModel.isLoading
    val showSetPasswordDialog by viewModel.showSetPasswordDialog
    val loginWithPasswordMode by viewModel.loginWithPasswordMode
    
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val activity = context as? Activity

    var newPasswordInput by remember { mutableStateOf("") }

    if (showSetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSetPasswordDialog() },
            title = { Text(stringResource(R.string.auth_set_password_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.auth_set_password_desc))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text(stringResource(R.string.auth_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPasswordInput.length >= 6) {
                        viewModel.setPasswordForPhoneUser(newPasswordInput) {
                            Toast.makeText(context, "Пароль установлен", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Минимум 6 символов", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSetPasswordDialog() }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
    
    val oneTapClient = remember { if (isPreview) null else Identity.getSignInClient(context) }
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = oneTapClient?.getSignInCredentialFromIntent(result.data)
                val idToken = credential?.googleIdToken
                if (idToken != null) {
                    viewModel.signInWithGoogle(idToken, onAuthSuccess)
                }
            } catch (e: ApiException) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Tabs for Login / Register
        TabRow(
            selectedTabIndex = if (isLogin) 0 else 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[if (isLogin) 0 else 1]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {}
        ) {
            Tab(
                selected = isLogin,
                onClick = { if (!isLogin) viewModel.toggleIsLogin() },
                text = { Text(stringResource(R.string.auth_login), style = MaterialTheme.typography.titleMedium) }
            )
            Tab(
                selected = !isLogin,
                onClick = { if (isLogin) viewModel.toggleIsLogin() },
                text = { Text(stringResource(R.string.auth_register), style = MaterialTheme.typography.titleMedium) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Mode Selector (Email / Phone)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(
                selected = authMode == "email",
                onClick = { viewModel.setAuthMode("email") },
                label = { Text(stringResource(R.string.auth_email_label)) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            FilterChip(
                selected = authMode == "phone",
                onClick = { viewModel.setAuthMode("phone") },
                label = { Text(stringResource(R.string.auth_phone_label)) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (authMode == "email") {
            OutlinedTextField(
                value = email,
                onValueChange = { viewModel.onEmailChange(it) },
                label = { Text(stringResource(R.string.auth_email_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text(stringResource(R.string.auth_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )
            if (!isLogin) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { viewModel.onConfirmPasswordChange(it) },
                    label = { Text(stringResource(R.string.auth_confirm_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
            }
        } else if (authMode == "phone") {
            if (loginWithPasswordMode) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { viewModel.onPhoneNumberChange(it) },
                    label = { Text(stringResource(R.string.auth_enter_phone) + " (+7...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.onPasswordChange(it) },
                    label = { Text(stringResource(R.string.auth_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                TextButton(onClick = { viewModel.toggleLoginWithPassword() }) {
                    Text(stringResource(R.string.auth_login_with_sms))
                }
            } else {
                if (verificationId == null) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { viewModel.onPhoneNumberChange(it) },
                        label = { Text(stringResource(R.string.auth_enter_phone) + " (+7...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                    TextButton(onClick = { viewModel.toggleLoginWithPassword() }) {
                        Text(stringResource(R.string.auth_login_with_password))
                    }
                } else {
                    Text(stringResource(R.string.auth_sms_code) + " -> $phoneNumber", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { viewModel.onOtpCodeChange(it) },
                        label = { Text(stringResource(R.string.auth_sms_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                }
            }
        }

        if (error != null) {
            Text(
                text = error!!, 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            Button(
                onClick = {
                    if (authMode == "email") {
                        if (isLogin) viewModel.signInWithEmail(onAuthSuccess)
                        else viewModel.signUpWithEmail(onAuthSuccess)
                    } else if (authMode == "phone") {
                        if (loginWithPasswordMode) {
                            viewModel.signInWithPhoneAndPassword(onAuthSuccess)
                        } else if (verificationId == null) {
                            activity?.let { viewModel.startPhoneVerification(it) }
                        } else {
                            viewModel.verifyOtp(context, onAuthSuccess)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (authMode == "phone" && verificationId == null) stringResource(R.string.auth_send_code)
                    else if (isLogin) stringResource(R.string.auth_login)
                    else stringResource(R.string.auth_register)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedButton(
                onClick = {
                    try {
                        val signInRequest = BeginSignInRequest.builder()
                            .setGoogleIdTokenRequestOptions(
                                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                    .setSupported(true)
                                    .setServerClientId(context.getString(R.string.default_web_client_id))
                                    .setFilterByAuthorizedAccounts(false)
                                    .build()
                            ).build()

                        oneTapClient?.beginSignIn(signInRequest)
                            ?.addOnSuccessListener { result ->
                                googleLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
                            }
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auth_google_sign_in))
            }
        }
    }
}
