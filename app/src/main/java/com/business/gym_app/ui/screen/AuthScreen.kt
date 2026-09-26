package com.business.gym_app.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym_app.ui.viewmodel.AuthViewModel
import com.business.gym_app.ui.viewmodel.SettingsViewModel
import com.business.gym_app.R
import com.business.gym_app.util.BiometricHelper
import com.business.gym_app.util.PinHelper

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
    val isLogin by viewModel.isLogin
    val regPhone by viewModel.regPhone
    val confirmPassword by viewModel.confirmPassword
    val privacyAgreed by viewModel.privacyAgreed

    val registeredMethod by viewModel.registeredMethod
    val isRegisteredUser = !registeredMethod.isNullOrBlank() || viewModel.hasSavedCredentials()

    val pinMode by viewModel.pinMode
    val pinValue by viewModel.pinValue
    val pinConfirm by viewModel.pinConfirm
    val pendingPinSetup by viewModel.pendingPinSetup
    val pinSetupAccount by viewModel.pinAccount
    val pinChangeReason by viewModel.pinChangeReason
    val pinExpired by viewModel.pinExpired

    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }
    // Если биометрия не сработала — показываем пароль для ручного ввода.
    var biometricFailed by remember { mutableStateOf(false) }
    // Разрешение на вход по биометрии хранится в профиле (Settings → тумблер).
    // Читаем флаг реактивно: после включения/выключения в профиле экран входа
    // сразу начнёт (или перестанет) предлагать биометрию.
    val biometricPrefs = remember(context) {
        context.getSharedPreferences("biometric_prefs", android.content.Context.MODE_PRIVATE)
    }
    var biometricAllowed by remember { mutableStateOf(biometricPrefs.getBoolean("biometric_enabled", false)) }
    DisposableEffect(biometricPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "biometric_enabled") {
                biometricAllowed = biometricPrefs.getBoolean("biometric_enabled", false)
            }
        }
        biometricPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { biometricPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val biometricAvailable = remember(context) { BiometricHelper.canAuthenticate(context) }
    // Автовход по биометрии: только для зарегистрированного пользователя,
    // нажавшего «Вход», пока биометрия ещё не падала.
    val autoBiometricLogin = remember(isLogin, isRegisteredUser, biometricAllowed, biometricAvailable, biometricFailed) {
        isLogin && isRegisteredUser && biometricAllowed && biometricAvailable && !biometricFailed
    }
    // PIN есть для текущего логина? Проверяем реактивно от сохранённых данных.
    val hasPinLogin = remember(isLogin, isRegisteredUser, otpEmail, otpPhone, pinSetupAccount, pendingPinSetup) {
        isLogin && isRegisteredUser && viewModel.hasPinForCurrentAccount(context)
    }
    val showPinLogin = isLogin && isRegisteredUser && hasPinLogin && pinMode
    // PIN нужно менять каждые 7 дней: если срок вышел — сразу просим задать новый.
    // Из диалога можно уйти на вход по паролю, но сам просроченный PIN не сработает.
    LaunchedEffect(isLogin, isRegisteredUser, hasPinLogin) {
        if (isLogin && isRegisteredUser && hasPinLogin && !pendingPinSetup) {
            val account = viewModel.pinLoginAccount(context)
            if (account.isNotBlank() && PinHelper.isPinExpired(context)) {
                viewModel.setPinMode(true)
                viewModel.requestPinSetup(account, AuthViewModel.PinChangeReason.EXPIRED)
            }
        }
    }
    // Смена пользователя/режима — снова пробуем биометрию первой,
    // PIN-режим сбрасываем (показываем обычную форму входа).
    LaunchedEffect(isLogin, registeredMethod) {
        biometricFailed = false
        if (pinMode) viewModel.setPinMode(false)
    }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    // В альбомной ориентации ограничиваем ширину формы авторизации для лучшего вида
    val contentModifier = if (isWideScreen) Modifier.widthIn(max = 400.dp).fillMaxWidth() else Modifier.fillMaxWidth()

    if (showAgreement) {
        AgreementDialog(onDismiss = { showAgreement = false })
    }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
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

            if (isLogin) {
                // Зарегистрированный пользователь: логин фиксирован (email или телефон),
                // режим менять нельзя. Биометрия срабатывает автоматически по кнопке «Вход»,
                // пароль просим только если биометрия не сработала.
                val useBiometricFirst = autoBiometricLogin
                if (registeredMethod == "email") {
                    LaunchedEffect(Unit) {
                        viewModel.setAuthMode("email")
                    }
                    OutlinedTextField(
                        value = otpEmail,
                        onValueChange = { viewModel.onOtpEmailChange(it) },
                        label = { Text(stringResource(R.string.auth_email_label)) },
                        modifier = contentModifier,
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Color.Red,
                            focusedBorderColor = Color.Red
                        )
                    )
                } else if (registeredMethod == "phone") {
                    LaunchedEffect(Unit) {
                        viewModel.setAuthMode("phone")
                    }
                    OutlinedTextField(
                        value = otpPhone,
                        onValueChange = { viewModel.onOtpPhoneChange(it) },
                        label = { Text(stringResource(R.string.auth_phone_hint)) },
                        modifier = contentModifier,
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedLabelColor = Color.Red,
                            focusedBorderColor = Color.Red
                        )
                    )
                } else {
                    // ПЕРЕКЛЮЧАТЕЛЬ Login/Register для не зарегистрованных
                    TabRow(
                        selectedTabIndex = if (authMode == "email") 0 else 1,
                        containerColor = Color.Transparent,
                        contentColor = Color.Red,
                        divider = {},
                        modifier = contentModifier,
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
                            text = { Text(stringResource(R.string.auth_email_label), color = if (authMode == "email") Color.Red else Color.Gray) }
                        )
                        Tab(
                            selected = authMode == "phone",
                            onClick = { viewModel.setAuthMode("phone") },
                            text = { Text(stringResource(R.string.auth_phone_label), color = if (authMode == "phone") Color.Red else Color.Gray) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (authMode == "email") {
                        OutlinedTextField(
                            value = otpEmail,
                            onValueChange = { viewModel.onOtpEmailChange(it) },
                            label = { Text(stringResource(R.string.auth_email_label)) },
                            modifier = contentModifier,
                            singleLine = true,
                            enabled = !isLoading,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedLabelColor = Color.Red,
                                focusedBorderColor = Color.Red
                            )
                        )
                    } else {
                        OutlinedTextField(
                            value = otpPhone,
                            onValueChange = { viewModel.onOtpPhoneChange(it) },
                            label = { Text(stringResource(R.string.auth_phone_hint)) },
                            modifier = contentModifier,
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
                }

                // Пароль всегда виден на экране входа; при включённой биометрии
                // сверху показываем подсказку про автовход по отпечатку/лицу.
                if (useBiometricFirst) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.biometric_login_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = contentModifier
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.onPasswordChange(it) },
                    label = { Text(stringResource(R.string.auth_password_hint)) },
                    modifier = contentModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // РЕГИСТРАЦИЯ
                Text(
                    text = stringResource(R.string.auth_register),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    modifier = contentModifier,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = otpEmail,
                    onValueChange = { viewModel.onOtpEmailChange(it) },
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    modifier = contentModifier,
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
                    value = regPhone,
                    onValueChange = { viewModel.onRegPhoneChange(it) },
                    label = { Text(stringResource(R.string.auth_phone_hint)) },
                    modifier = contentModifier,
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
                    modifier = contentModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { viewModel.onConfirmPasswordChange(it) },
                    label = { Text(stringResource(R.string.auth_confirm_password_hint)) },
                    modifier = contentModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = contentModifier.padding(vertical = 8.dp)
                ) {

                    Checkbox(
                        checked = privacyAgreed,
                        onCheckedChange = { viewModel.onPrivacyAgreementChange(it) },
                        colors = CheckboxDefaults.colors(checkedColor = Color.Red)
                    )
                    
                    val annotatedString = buildAnnotatedString {
                        append(stringResource(R.string.privacy_accept_prefix))
                        pushStringAnnotation(tag = "agreement", annotation = "agreement")
                        withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append(stringResource(R.string.privacy_accept_link))
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "agreement", start = offset, end = offset)
                                .firstOrNull()?.let {
                                    showAgreement = true
                                }
                        }
                    )
                }
            }

            if (error != null) {
                // Сообщения об успехе приходят через тот же канал, что и ошибки,
                // поэтому сравниваем с локализованными строками успеха.
                val successMessages = listOf(
                    stringResource(R.string.auth_otp_sent),
                    stringResource(R.string.application_sent)
                )
                Text(
                    text = error!!,
                    color = if (error in successMessages) Color.Green else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.Red)
            } else if (showPinLogin) {
                // Быстрый вход по PIN из 4 цифр (PIN привязан к сохранённому логину).
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { viewModel.onPinChange(it) },
                    label = { Text(stringResource(R.string.pin_enter)) },
                    modifier = contentModifier,
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = Color.Red,
                        focusedBorderColor = Color.Red
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.signInWithPin(
                            context,
                            onSuccess = { onAuthSuccess(it) },
                            onNeedPassword = { viewModel.setPinMode(false) },
                            onPinExpired = { /* диалог смены PIN уже открыт в VM */ }
                        )
                    },
                    modifier = contentModifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(
                        stringResource(R.string.pin_login_button),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.setPinMode(false) },
                    modifier = contentModifier
                ) {
                    Text(stringResource(R.string.pin_forgot), color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                val loginButtonText = stringResource(R.string.auth_login_password)
                val registerButtonText = stringResource(R.string.auth_register).uppercase()
                val biometricLaunchFailedText = stringResource(R.string.biometric_launch_failed)
                Button(
                    onClick = {
                        if (isLogin) {
                            if (autoBiometricLogin) {
                                // Приоритет — автоматический отпечаток/лицо.
                                // Не сработала — уходим в PIN (если задан) или в пароль.
                                // Отмена диалога — остаёмся на экране входа.
                                val activity = context as? FragmentActivity
                                if (activity != null) {
                                    BiometricHelper.showBiometricPrompt(
                                        activity = activity,
                                        onSuccess = {
                                            viewModel.signInWithBiometrics(
                                                onSuccess = { onAuthSuccess(it) },
                                                onNeedPassword = { biometricFailed = true }
                                            )
                                        },
                                        onError = { err ->
                                            biometricFailed = true
                                            if (hasPinLogin) viewModel.setPinMode(true)
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        },
                                        onCancel = { /* остаёмся на вводе */ }
                                    )
                                } else {
                                    // Activity недоступна — PIN или обычный вход по паролю.
                                    biometricFailed = true
                                    if (hasPinLogin) viewModel.setPinMode(true)
                                    else Toast.makeText(context, biometricLaunchFailedText, Toast.LENGTH_SHORT).show()
                                }
                            } else if (hasPinLogin && !pinMode) {
                                // PIN задан, биометрия не разрешена — открываем быстрый вход по PIN.
                                viewModel.setPinMode(true)
                            } else {
                                viewModel.signInWithEmail { onAuthSuccess(it) }
                            }
                        } else {
                            viewModel.signUpWithEmail { newEmail ->
                                // После регистрации предлагаем задать PIN из 4 цифр.
                                viewModel.requestPinSetup(newEmail)
                            }
                        }
                    },
                    modifier = contentModifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isLogin || privacyAgreed, // Кнопка регистрации активна только при согласии
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        if (isLogin) loginButtonText else registerButtonText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLogin && isRegisteredUser && hasPinLogin && !pinMode) {
                    // Есть сохранённый PIN — предлагаем быстрый вход вместо пароля.
                    OutlinedButton(
                        onClick = { viewModel.setPinMode(true) },
                        modifier = contentModifier.height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                    ) {
                        Text(
                            stringResource(R.string.pin_login_button),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isLogin) {
                    OutlinedButton(
                        onClick = { 
                            viewModel.loginAsGuest { onAuthSuccess(AuthViewModel.GUEST_EMAIL) }
                        },
                        modifier = contentModifier.height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                    ) {
                        Text(
                            stringResource(R.string.auth_login_guest),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { viewModel.toggleIsLogin() }, modifier = contentModifier) {
                        Text(stringResource(R.string.auth_new_here), color = Color.Gray)
                    }
                } else {
                    TextButton(onClick = { viewModel.toggleIsLogin() }, modifier = contentModifier) {
                        Text(stringResource(R.string.auth_already_have), color = Color.Gray)
                    }
                }
            }
        }
    }

    // Диалог создания/смены PIN: ввод + подтверждение из 4 цифр.
    // MANUAL — сразу после регистрации («Пропустить» = войти, пользователь зарегистрирован).
    // EXPIRING_SOON — после успешного входа по PIN (вход уже произошёл).
    // EXPIRED — срок 7 дней вышел: вход по PIN не подтверждён, поэтому закрыть можно
    // только входом по паролю — onDismiss здесь НЕ вызывает onAuthSuccess.
    if (pendingPinSetup) {
        val pinSavedText = stringResource(R.string.pin_saved)
        val pinExpiredText = stringResource(R.string.pin_expired)
        val isExpiredReason = pinExpired || pinChangeReason == AuthViewModel.PinChangeReason.EXPIRED
        val isManual = pinChangeReason == AuthViewModel.PinChangeReason.MANUAL
        val toastMessage = if (isExpiredReason) pinExpiredText else pinSavedText
        PinEditDialog(
            pinValue = pinValue,
            pinConfirm = pinConfirm,
            pinError = error,
            onPinChange = { viewModel.onPinChange(it) },
            onPinConfirmChange = { viewModel.onPinConfirmChange(it) },
            onDismiss = {
                viewModel.dismissPinSetup()
                when {
                    isExpiredReason -> viewModel.setPinMode(false) // остаёмся вводить пароль
                    isManual -> onAuthSuccess(pinSetupAccount)
                }
            },
            onSave = {
                viewModel.savePinCode(pinSetupAccount) {
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                    if (isExpiredReason) {
                        // Новый PIN задан — предлагаем продолжить вход по нему.
                        viewModel.setPinMode(true)
                    } else {
                        onAuthSuccess(pinSetupAccount)
                    }
                }
            },
            dismissTextRes = if (isExpiredReason) R.string.pin_use_password else R.string.pin_skip,
            titleRes = if (isManual) R.string.pin_create_title else R.string.pin_change_title,
            subtitleRes = if (isManual) R.string.pin_create_subtitle else R.string.pin_change_subtitle,
            dismissible = true
        )
    }
}


@Composable
fun AgreementDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.e_agreement_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.close), tint = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.agreement_dialog_text),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
