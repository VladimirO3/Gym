package com.business.gym.ui.screen

import android.app.Activity
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
    val phoneNumber by viewModel.phoneNumber
    val otpCode by viewModel.otpCode
    val verificationId by viewModel.verificationId
    val authMode by viewModel.authMode
    val isLogin by viewModel.isLogin
    val error by viewModel.error
    val isLoading by viewModel.isLoading
    
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val activity = context as? Activity
    
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
                // Error handled in ViewModel or locally if needed
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when(authMode) {
                "phone" -> stringResource(R.string.auth_phone_title)
                else -> if (isLogin) stringResource(R.string.auth_login) else stringResource(R.string.auth_register)
            },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Mode Selector
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(
                selected = authMode == "email",
                onClick = { viewModel.setAuthMode("email") },
                label = { Text(stringResource(R.string.auth_email_label)) }
            )
            FilterChip(
                selected = authMode == "phone",
                onClick = { viewModel.setAuthMode("phone") },
                label = { Text(stringResource(R.string.auth_phone_label)) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

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
        } else if (authMode == "phone") {
            if (verificationId == null) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { viewModel.onPhoneNumberChange(it) },
                    label = { Text(stringResource(R.string.auth_enter_phone) + " (+7...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
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

        if (error != null) {
            Text(
                text = error!!, 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            Button(
                onClick = {
                    if (authMode == "email") {
                        if (isLogin) viewModel.signInWithEmail(onAuthSuccess)
                        else viewModel.signUpWithEmail(onAuthSuccess)
                    } else if (authMode == "phone") {
                        if (verificationId == null) {
                            activity?.let { viewModel.startPhoneVerification(it) }
                        } else {
                            viewModel.verifyOtp(context, onAuthSuccess)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (authMode == "phone" && verificationId == null) stringResource(R.string.auth_send_code)
                    else if (isLogin) stringResource(R.string.auth_login)
                    else stringResource(R.string.auth_register)
                )
            }
            
            if (authMode == "email") {
                TextButton(onClick = { viewModel.toggleIsLogin() }) {
                    Text(if (isLogin) stringResource(R.string.auth_new_here) else stringResource(R.string.auth_already_have))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auth_google_sign_in))
            }
        }
    }
}
