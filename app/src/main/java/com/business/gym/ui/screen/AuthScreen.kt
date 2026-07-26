package com.business.gym.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
    AuthScreenContent(
        email = viewModel.email.value,
        password = viewModel.password.value,
        confirmPassword = viewModel.confirmPassword.value,
        phoneNumber = viewModel.phoneNumber.value,
        otpCode = viewModel.otpCode.value,
        verificationId = viewModel.verificationId.value,
        authMode = viewModel.authMode.value,
        isLogin = viewModel.isLogin.value,
        error = viewModel.error.value,
        isLoading = viewModel.isLoading.value,
        showSetPasswordDialog = viewModel.showSetPasswordDialog.value,
        loginWithPasswordMode = viewModel.loginWithPasswordMode.value,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onOtpCodeChange = viewModel::onOtpCodeChange,
        onAuthModeChange = viewModel::setAuthMode,
        onToggleIsLogin = viewModel::toggleIsLogin,
        onToggleLoginWithPassword = viewModel::toggleLoginWithPassword,
        onDismissSetPasswordDialog = viewModel::dismissSetPasswordDialog,
        onSetPasswordForPhoneUser = { pwd, success -> viewModel.setPasswordForPhoneUser(pwd, success) },
        onSignInWithEmail = { viewModel.signInWithEmail(onAuthSuccess) },
        onSignUpWithEmail = { viewModel.signUpWithEmail(onAuthSuccess) },
        onSignInWithPhoneAndPassword = { viewModel.signInWithPhoneAndPassword(onAuthSuccess) },
        onStartPhoneVerification = { activity -> viewModel.startPhoneVerification(activity) },
        onVerifyOtp = { context, success -> viewModel.verifyOtp(context, { success() }) },
        onSignInWithGoogle = { idToken -> viewModel.signInWithGoogle(idToken, onAuthSuccess) }
    )
}

@Composable
fun AuthScreenContent(
    email: String,
    password: String,
    confirmPassword: String,
    phoneNumber: String,
    otpCode: String,
    verificationId: String?,
    authMode: String,
    isLogin: Boolean,
    error: String?,
    isLoading: Boolean,
    showSetPasswordDialog: Boolean,
    loginWithPasswordMode: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onOtpCodeChange: (String) -> Unit,
    onAuthModeChange: (String) -> Unit,
    onToggleIsLogin: () -> Unit,
    onToggleLoginWithPassword: () -> Unit,
    onDismissSetPasswordDialog: () -> Unit,
    onSetPasswordForPhoneUser: (String, () -> Unit) -> Unit,
    onSignInWithEmail: () -> Unit,
    onSignUpWithEmail: () -> Unit,
    onSignInWithPhoneAndPassword: () -> Unit,
    onStartPhoneVerification: (Activity) -> Unit,
    onVerifyOtp: (android.content.Context, () -> Unit) -> Unit,
    onSignInWithGoogle: (String) -> Unit
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val activity = context as? Activity

    var newPasswordInput by remember { mutableStateOf("") }

    if (showSetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { onDismissSetPasswordDialog() },
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
                        onSetPasswordForPhoneUser(newPasswordInput) {
                            Toast.makeText(context, "Пароль установлен", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Минимум 6 символов", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { onDismissSetPasswordDialog() }) { Text(stringResource(R.string.btn_cancel)) }
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
                    onSignInWithGoogle(idToken)
                }
            } catch (e: ApiException) {
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val formModifier = if (isWideScreen) Modifier.width(480.dp) else Modifier.fillMaxWidth()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Tabs for Login / Register
        TabRow(
            selectedTabIndex = if (isLogin) 0 else 1,
            modifier = formModifier.padding(horizontal = 16.dp),
            containerColor = Color.Transparent,
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
                onClick = { if (!isLogin) onToggleIsLogin() },
                text = { Text(stringResource(R.string.auth_login), style = MaterialTheme.typography.titleMedium) }
            )
            Tab(
                selected = !isLogin,
                onClick = { if (isLogin) onToggleIsLogin() },
                text = { Text(stringResource(R.string.auth_register), style = MaterialTheme.typography.titleMedium) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Mode Selector (Email / Phone)
        Row(modifier = formModifier, horizontalArrangement = Arrangement.Center) {
            FilterChip(
                selected = authMode == "email",
                onClick = { onAuthModeChange("email") },
                label = { Text(stringResource(R.string.auth_email_label)) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Red,
                    selectedLabelColor = Color.White,
                    labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
            FilterChip(
                selected = authMode == "phone",
                onClick = { onAuthModeChange("phone") },
                label = { Text(stringResource(R.string.auth_phone_label)) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Red,
                    selectedLabelColor = Color.White,
                    labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (authMode == "email") {
            OutlinedTextField(
                value = email,
                onValueChange = { onEmailChange(it) },
                label = { Text(stringResource(R.string.auth_email_hint)) },
                modifier = formModifier,
                singleLine = true,
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedLabelColor = Color.Red,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { onPasswordChange(it) },
                label = { Text(stringResource(R.string.auth_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = formModifier,
                singleLine = true,
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedLabelColor = Color.Red,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            )
            if (!isLogin) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { onConfirmPasswordChange(it) },
                    label = { Text(stringResource(R.string.auth_confirm_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = formModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
            }
        } else if (authMode == "phone") {
            if (loginWithPasswordMode) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { onPhoneNumberChange(it) },
                    label = { Text(stringResource(R.string.auth_enter_phone) + " (+7...)") },
                    modifier = formModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it) },
                    label = { Text(stringResource(R.string.auth_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = formModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                TextButton(onClick = { onToggleLoginWithPassword() }) {
                    Text(stringResource(R.string.auth_login_with_sms), color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                if (verificationId == null) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { onPhoneNumberChange(it) },
                        label = { Text(stringResource(R.string.auth_enter_phone) + " (+7...)") },
                        modifier = formModifier,
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Color.Red,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    )
                    TextButton(onClick = { onToggleLoginWithPassword() }) {
                        Text(stringResource(R.string.auth_login_with_password), color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Text(
                        stringResource(R.string.auth_sms_code) + " -> $phoneNumber", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { onOtpCodeChange(it) },
                        label = { Text(stringResource(R.string.auth_sms_code)) },
                        modifier = formModifier,
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Color.Red,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        if (error != null) {
            Text(
                text = error, 
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
                        if (isLogin) onSignInWithEmail()
                        else onSignUpWithEmail()
                    } else if (authMode == "phone") {
                        if (loginWithPasswordMode) {
                            onSignInWithPhoneAndPassword()
                        } else if (verificationId == null) {
                            activity?.let { onStartPhoneVerification(it) }
                        } else {
                            onVerifyOtp(context) {}
                        }
                    }
                },
                modifier = formModifier.height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (authMode == "phone" && verificationId == null) stringResource(R.string.auth_send_code)
                    else if (isLogin) stringResource(R.string.auth_login)
                    else stringResource(R.string.auth_register),
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = formModifier)
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
                modifier = formModifier.height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auth_google_sign_in), color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AuthScreenPreview() {
    com.business.gym.ui.theme.GymTheme {
        AuthScreenContent(
            email = "test@example.com",
            password = "password123",
            confirmPassword = "",
            phoneNumber = "+79001112233",
            otpCode = "",
            verificationId = null,
            authMode = "email",
            isLogin = true,
            error = null,
            isLoading = false,
            showSetPasswordDialog = false,
            loginWithPasswordMode = false,
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onPhoneNumberChange = {},
            onOtpCodeChange = {},
            onAuthModeChange = {},
            onToggleIsLogin = {},
            onToggleLoginWithPassword = {},
            onDismissSetPasswordDialog = {},
            onSetPasswordForPhoneUser = { _, _ -> },
            onSignInWithEmail = {},
            onSignUpWithEmail = {},
            onSignInWithPhoneAndPassword = {},
            onStartPhoneVerification = {},
            onVerifyOtp = { _, _ -> },
            onSignInWithGoogle = {}
        )
    }
}
