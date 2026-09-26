package com.business.gym_app.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym_app.ui.viewmodel.SettingsViewModel
import com.business.gym_app.ui.viewmodel.AuthViewModel
import com.business.gym_app.R
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import coil.request.ImageRequest
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.ui.viewmodel.DailyWorkout
import com.business.gym_app.ui.viewmodel.Exercise
import com.business.gym_app.ui.component.rememberLocalizedWorkout
import com.business.gym_app.ui.component.rememberTranslatedTitle
import com.business.gym_app.ui.component.rememberTranslatedText
import com.business.gym_app.util.BiometricHelper
import com.business.gym_app.util.NotificationHelper
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun SettingsScreen(
    currentUserEmail: String?,
    onLogout: () -> Unit,
    onGoToCart: () -> Unit,
    onOpenWorkout: (String) -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    // Используем fillMaxWidth с ограничением по максимальной ширине для центрирования
    val contentModifier = if (isWideScreen) Modifier.fillMaxWidth().widthIn(max = 800.dp) else Modifier.fillMaxWidth()
    
    val isAdmin by authViewModel.isAdminState
    val isGuest by authViewModel.isGuest
    
    val userName by viewModel.userName
    val userAge by viewModel.userAge
    val avatarUrl by viewModel.avatarUrl
    val dailyPlan by viewModel.dailyPlan
    val isUpdating by viewModel.isUpdatingProfile
    val jwtToken by authViewModel.jwtToken

    var nameInput by remember { mutableStateOf(userName) }
    var ageInput by remember { mutableStateOf(userAge?.toString() ?: "") }
    var isEditMode by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val canEditProfile = currentUserEmail != null && !isAdmin && !isGuest

    LaunchedEffect(userName, userAge) {
        nameInput = userName
        ageInput = userAge?.toString() ?: ""
    }

    val changePasswordSuccessText = stringResource(R.string.change_password_success)

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onChangePassword = { oldPass, newPass, onError ->
                viewModel.changePassword(
                    context = context,
                    oldPass = oldPass,
                    newPass = newPass,
                    onSuccess = {
                        showChangePasswordDialog = false
                        Toast.makeText(context, changePasswordSuccessText, Toast.LENGTH_SHORT).show()
                    },
                    onError = onError
                )
            },
            isLoading = viewModel.isChangingPassword.value
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.account_delete_title), color = Color.Red) },
            text = { Text(stringResource(R.string.account_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        authViewModel.deleteAccount { onLogout() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(stringResource(R.string.delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            }
        )
    }

    if (showIpDialog) {
        var ipInput by remember { mutableStateOf(viewModel.serverIp.value) }
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text(stringResource(R.string.auth_server_settings)) },
            text = {
                Column {
                    Text(stringResource(R.string.auth_server_ip_hint), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text(stringResource(R.string.auth_ip_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setServerIp(context, null, ipInput)
                    showIpDialog = false
                }) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadAvatar(context, it, jwtToken) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 32.dp else 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ЗАГОЛОВОК С ИНДИКАТОРОМ
        Box(modifier = contentModifier) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(10.dp)
                    .background(if (jwtToken != null) Color.Green else Color.Red, CircleShape)
            )

            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { authViewModel.loadSession(context) },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.profile_edit), tint = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (isAdmin) {
            Card(
                modifier = contentModifier.padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null, tint = Color.Blue, modifier = Modifier.size(48.dp))
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.status_administrator),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Blue,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        if (isGuest && !isAdmin) {
            Card(
                modifier = contentModifier.padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                border = BorderStroke(2.dp, Color.Red)
            ) {
                Text(
                    text = stringResource(R.string.guest_logged_in),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Red,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (canEditProfile) {
            // Блок профиля
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
                modifier = contentModifier,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = contentModifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val avatarSize = if (isWideScreen) 140.dp else 100.dp
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(Color.Gray)
                            .then(if (isEditMode || userName.isBlank()) Modifier.clickable { photoPickerLauncher.launch("image/*") } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            val fullAvatarUrl = remember(avatarUrl) {
                                NewsApiService.getFullUrl(context, avatarUrl)
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(fullAvatarUrl)
                                    .setHeader("Authorization", "Bearer $jwtToken")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.cd_avatar),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Default.Person),
                                placeholder = rememberVectorPainter(Icons.Default.Person)
                            )
                            
                            // Кнопка удаления фото (только в режиме редактирования)
                            if (isEditMode && !isUpdating) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .clickable { viewModel.deleteAvatar(context) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(50.dp), tint = Color.White)
                        }
                        
                        if ((isEditMode || userName.isBlank()) && !isUpdating) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhotoCamera, null, tint = Color.White.copy(alpha = 0.7f))
                            }
                        }

                        if (isUpdating) {
                            CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(avatarSize))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isEditMode && userName.isNotBlank()) {
                        Text(
                            text = rememberTranslatedText(userName),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (userAge != null) {
                            Text(
                                text = stringResource(R.string.age_value, userAge.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { isEditMode = true }) {
                                Text(stringResource(R.string.profile_edit), color = Color.Gray, fontSize = 12.sp)
                            }
                            if (canEditProfile) {
                                Text("|", color = Color.Gray, fontSize = 12.sp)
                                TextButton(onClick = { showChangePasswordDialog = true }) {
                                    Text(stringResource(R.string.change_password_button), color = Color.Red, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = onGoToCart,
                            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.my_cart), color = Color.White, fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // История заказов
                        val orders by viewModel.orderHistory
                        if (orders.isNotEmpty()) {
                            Text(
                                stringResource(R.string.order_history), 
                                style = MaterialTheme.typography.titleSmall, 
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                textAlign = TextAlign.Start
                            )
                            orders.forEach { order ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(R.string.order_number, order.id.take(8)), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(order.status, color = if (order.status == "completed") Color.Green else Color.Yellow, fontSize = 10.sp)
                                        }
                                        Text("${order.totalPrice} ₽", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text(stringResource(R.string.name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) ageInput = it },
                            label = { Text(stringResource(R.string.age_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (userName.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { 
                                        isEditMode = false
                                        nameInput = userName
                                        ageInput = userAge?.toString() ?: ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                            
                            Button(
                                onClick = { 
                                    viewModel.updateProfile(context, nameInput, ageInput.toIntOrNull(), jwtToken)
                                    isEditMode = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                enabled = !isUpdating && nameInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.save), color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // --- КАЛЕНДАРЬ ЗАМЕТОК ---
            Text(
                text = stringResource(R.string.my_workouts_notes),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
                modifier = contentModifier,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            val calendarModifier = if (isWideScreen) Modifier.widthIn(max = 500.dp).fillMaxWidth() else contentModifier
            GymCalendar(viewModel, calendarModifier)

            Spacer(modifier = Modifier.height(32.dp))

            // --- ПЛАН ТРЕНИРОВОК ДЛЯ НОВИЧКОВ ---
            TrainingPlanSection(dailyPlan, contentModifier)

            Spacer(modifier = Modifier.height(24.dp))
        }

        val canUseBiometrics = remember(context) { BiometricHelper.canAuthenticate(context) }
        if (!isGuest) {
            var biometricEnabled by remember { mutableStateOf(BiometricHelper.isBiometricEnabled(context)) }
            
            Card(
                modifier = contentModifier,
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.2f)),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.biometric_login_button), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    }
                    // Тексты тоста поднимаем в composable-область: stringResource
                    // нельзя вызывать внутри лямбды onCheckedChange.
                    val biometricEnabledText = stringResource(R.string.biometric_login_enabled)
                    val biometricDisabledText = stringResource(R.string.biometric_login_disabled)
                    Switch(
                        checked = biometricEnabled,
                        enabled = canUseBiometrics,
                        onCheckedChange = {
                            biometricEnabled = it
                            BiometricHelper.setBiometricEnabled(context, it)
                            val msg = if (it) biometricEnabledText else biometricDisabledText
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Red, checkedTrackColor = Color.Red.copy(alpha = 0.5f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            QuickPinSection(
                contentModifier = contentModifier,
                authViewModel = authViewModel
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Блок настройки автозапуска и фона для стабильного получения уведомлений
        Card(
            modifier = contentModifier,
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.2f)),
            border = BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.notifications_background_title), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                // Статус разрешения: без POST_NOTIFICATIONS (Android 13+) уведомления
                // о сообщениях не показываются, и пользователь должен это видеть.
                var notificationsAllowed by remember {
                    mutableStateOf(NotificationHelper.areNotificationsEnabled(context))
                }
                // Пользователь может выдать разрешение в системных настройках и вернуться —
                // статус должен обновиться, поэтому перечитываем его при показе экрана.
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            notificationsAllowed = NotificationHelper.areNotificationsEnabled(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (notificationsAllowed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (notificationsAllowed) Color.Green else Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (notificationsAllowed) {
                            stringResource(R.string.notifications_allowed)
                        } else {
                            stringResource(R.string.notifications_blocked_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (notificationsAllowed) Color.Green else Color.Red
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.notifications_autostart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(12.dp))
                // Тексты тоста поднимаем в composable-область: stringResource
                // нельзя вызывать внутри колбэка rememberLauncherForActivityResult.
                val notificationsOnText = stringResource(R.string.notifications_turned_on)
                val notificationsOffText = stringResource(R.string.notifications_still_off)
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    notificationsAllowed = NotificationHelper.areNotificationsEnabled(context)
                    Toast.makeText(
                        context,
                        if (granted) notificationsOnText else notificationsOffText,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (!notificationsAllowed) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationHelper.openNotificationSettings(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.enable_notifications), fontSize = 12.sp, color = Color.White)
                        }
                    }
                    Button(
                        onClick = { NotificationHelper.openAutostartAndBatterySettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.configure_autostart), fontSize = 12.sp, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.notifications_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.clickable { NotificationHelper.openNotificationSettings(context) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.settings_language), 
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red,
            modifier = contentModifier,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        val currentLocale = if (AppCompatDelegate.getApplicationLocales().isEmpty) "system" 
                           else AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "system"
        
        Column(modifier = contentModifier, horizontalAlignment = Alignment.Start) {
            LanguageOption("system", currentLocale, stringResource(R.string.language_system)) { viewModel.setLanguage(context, currentUserEmail, it) }
            LanguageOption("en", currentLocale, stringResource(R.string.language_english)) { viewModel.setLanguage(context, currentUserEmail, it) }
            LanguageOption("ru", currentLocale, stringResource(R.string.language_russian)) { viewModel.setLanguage(context, currentUserEmail, it) }
        }

        if (isAdmin) {
            AdminWorkoutSection(
                viewModel = viewModel,
                contentModifier = contentModifier,
                onOpenWorkout = { onOpenWorkout(it.id) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = contentModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.registration_requests), 
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showIpDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_server_settings), tint = Color.Gray)
                }
            }
            
            val pendingUsers by authViewModel.pendingUsers
            LaunchedEffect(Unit) {
                authViewModel.fetchPendingUsers()
            }

            if (pendingUsers.isEmpty()) {
                Text(
                    stringResource(R.string.no_new_requests), 
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = contentModifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                pendingUsers.forEach { user ->
                    Card(
                        modifier = contentModifier.padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(user.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Row {
                                Button(
                                    onClick = { authViewModel.approveUser(user) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.auth_register), color = Color.Black, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(4.dp))
                                Button(
                                    onClick = { authViewModel.makeAdmin(user) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.administrator), color = Color.White, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { authViewModel.deleteUser(user) }
                                ) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.reject), tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (currentUserEmail != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = if (isGuest) stringResource(R.string.guest_mode, currentUserEmail)
                else stringResource(R.string.logged_in_as, currentUserEmail), 
                style = MaterialTheme.typography.bodyMedium, 
                color = if (isGuest) Color.Red else Color.Gray,
                fontWeight = if (isGuest) FontWeight.Bold else FontWeight.Normal,
                modifier = contentModifier,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auth_logout), color = Color.White)
            }

            if (!isGuest && !isAdmin) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_account), color = Color.Gray, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun GymCalendar(viewModel: SettingsViewModel, modifier: Modifier) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7 // adjustment for grid
    
    val dailyNotes by viewModel.dailyNotes
    val selectedNote = dailyNotes.find { it.date == selectedDate.toString() }?.note ?: ""
    
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(stringResource(R.string.note_for_date, selectedDate)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text(stringResource(R.string.note_hint)) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveNote(selectedDate, noteText)
                    showNoteDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.deleteNote(selectedDate)
                    showNoteDialog = false 
                }) { Text(stringResource(R.string.delete), color = Color.Red) }
            }
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Переключатель месяца
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Color.White)
                }
                Text(
                    text = "${currentMonth.month.getDisplayName(
                        TextStyle.FULL_STANDALONE,
                        androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                    )} ${currentMonth.year}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Дни недели
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    stringResource(R.string.weekday_mon),
                    stringResource(R.string.weekday_tue),
                    stringResource(R.string.weekday_wed),
                    stringResource(R.string.weekday_thu),
                    stringResource(R.string.weekday_fri),
                    stringResource(R.string.weekday_sat),
                    stringResource(R.string.weekday_sun)
                ).forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Сетка календаря
            val totalCells = ((daysInMonth + firstDayOfMonth + 6) / 7) * 7
            for (i in 0 until totalCells step 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (j in 0 until 7) {
                        val dayIndex = i + j - firstDayOfMonth + 1
                        val isCurrentMonthDay = dayIndex in 1..daysInMonth
                        val date = if (isCurrentMonthDay) currentMonth.atDay(dayIndex) else null
                        val hasNote = dailyNotes.any { it.date == date.toString() }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        date == selectedDate -> Color.Red
                                        date == LocalDate.now() -> Color.Red.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = isCurrentMonthDay) {
                                    if (date != null) {
                                        selectedDate = date
                                        noteText = dailyNotes.find { it.date == date.toString() }?.note ?: ""
                                        showNoteDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrentMonthDay) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayIndex",
                                        color = if (date == selectedDate) Color.White else Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                    if (hasNote) {
                                        Box(Modifier.size(4.dp).background(Color.Cyan, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (selectedNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color.Cyan.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.note_value, selectedNote),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Cyan
                    )
                }
            }
        }
    }
}

@Composable
fun TrainingPlanSection(plan: String?, modifier: Modifier) {
    val context = LocalContext.current
    var selectedTutorialImage by remember { mutableStateOf<String?>(null) }
    var selectedExerciseName by remember { mutableStateOf("") }

    if (selectedTutorialImage != null) {
        AlertDialog(
            onDismissRequest = { selectedTutorialImage = null },
            title = { Text(selectedExerciseName, color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExerciseImage(
                        url = selectedTutorialImage,
                        contentDescription = stringResource(R.string.cd_tutorial),
                        // Fit + диапазон высоты: фото показывается целиком, ничего не обрезается
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.execution_technique), 
                        fontSize = 13.sp, 
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = { selectedTutorialImage = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text(stringResource(R.string.got_it), color = Color.White)
                }
            }
        )
    }

    val dailyExerciseLabel = stringResource(R.string.daily_exercise)
    val trainingPlanLabel = stringResource(R.string.training_plan)

    // Разбираем план в модель. Перевод выполняется отдельно: встроенный словарь +
    // Google Cloud Translation для всего, чего в словаре нет (программы администратора).
    val planWorkout = remember(plan, dailyExerciseLabel, trainingPlanLabel) {
        if (plan.isNullOrBlank()) null
        else if (plan.startsWith("{")) {
            try {
                com.google.gson.Gson().fromJson(plan, com.business.gym_app.ui.viewmodel.DailyWorkout::class.java)
            } catch (e: Exception) { null }
        } else {
            // Конвертация старого текстового формата в новый для отображения
            val exercises = plan.split(",").map { 
                val defaultUrl = "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises/Bodyweight_Squats/0.jpg"
                com.business.gym_app.ui.viewmodel.Exercise(
                    it.trim(), 
                    defaultUrl, 
                    dailyExerciseLabel,
                    defaultUrl
                )
            }
            com.business.gym_app.ui.viewmodel.DailyWorkout(trainingPlanLabel, exercises)
        }
    }
    val workout = rememberLocalizedWorkout(planWorkout)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
    ) {
        Column {
            if (workout?.coverUrl != null) {
                ExerciseImage(
                    url = workout.coverUrl,
                    contentDescription = stringResource(R.string.cd_workout_cover),
                    // Загруженная обложка показывается полностью: Fit вписывает фото в блок,
                    // а heightIn не даёт высокому фото сжаться в узкую полоску (Crop его обрезал).
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 340.dp),
                    contentScale = ContentScale.Fit
                )
            }
            
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FitnessCenter, null, tint = Color.Red)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = workout?.title ?: stringResource(R.string.training_plan),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Red,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Text(
                    text = stringResource(R.string.personal_plan, LocalDate.now()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                if (workout != null) {
                    workout.exercises.forEach { exercise ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    if (exercise.tutorialImageUrl != null) {
                                        selectedTutorialImage = exercise.tutorialImageUrl
                                        selectedExerciseName = exercise.name
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray.copy(alpha = 0.3f))
                                ) {
                                    ExerciseImage(
                                        url = exercise.iconUrl,
                                        contentDescription = exercise.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exercise.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = exercise.desc,
                                        color = Color.Red.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (exercise.tutorialImageUrl != null) {
                                        Text(
                                            text = stringResource(R.string.tap_for_instruction),
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.tap_exercise_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            }
        }
    }
}

@Composable
fun ExerciseImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    // По умолчанию фото не обрезаем: Fit показывает изображение целиком
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) {
        Box(modifier = modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FitnessCenter, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        }
        return
    }

    // Список зеркал для GitHub контента
    val mirrors = remember(url) {
        val list = mutableListOf<String>()
        list.add(url)
        
        // Пытаемся извлечь путь упражнения, если это GitHub URL любого вида
        val exercisesIndex = url.indexOf("exercises/")
        if (exercisesIndex != -1) {
            val path = url.substring(exercisesIndex + "exercises/".length)
            
            // Mirror 1: jsDelivr (самый стабильный)
            val jsDelivr = "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises/$path"
            if (!list.contains(jsDelivr)) list.add(jsDelivr)
            
            // Mirror 2: Image Proxy (wsrv.nl - крайне надежный)
            val wsrv = "https://wsrv.nl/?url=raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$path"
            if (!list.contains(wsrv)) list.add(wsrv)

            // Mirror 3: Alt Proxy (images.weserv.nl)
            val weserv = "https://images.weserv.nl/?url=raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$path"
            if (!list.contains(weserv)) list.add(weserv)

            // Mirror 4: GitHub Raw
            val raw = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$path"
            if (!list.contains(raw)) list.add(raw)
            
            // Mirror 5: GitMirror
            val gitMirror = "https://raw.gitmirror.com/yuhonas/free-exercise-db/main/exercises/$path"
            if (!list.contains(gitMirror)) list.add(gitMirror)
            
            // Mirror 6: Proxy
            val proxy = "https://ghproxy.net/https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$path"
            if (!list.contains(proxy)) list.add(proxy)
        }
        list.distinct()
    }

    var currentMirrorIndex by remember(url) { mutableIntStateOf(0) }
    val currentUrl = mirrors.getOrElse(currentMirrorIndex) { url }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(currentUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        error = rememberVectorPainter(Icons.Default.Warning),
        placeholder = rememberVectorPainter(Icons.Default.Image),
        onError = {
            if (currentMirrorIndex < mirrors.size - 1) {
                currentMirrorIndex++
                Log.d("ExerciseImage", "Mirror failed: $currentUrl, switching index to $currentMirrorIndex")
            }
        }
    )
}

@Composable
fun LanguageOption(lang: String, currentLang: String, label: String, onClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().clickable { onClick(lang) }.padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = currentLang == lang, 
            onClick = { onClick(lang) },
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Red, 
                unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChangePassword: (oldPass: String, newPass: String, onError: (String) -> Unit) -> Unit,
    isLoading: Boolean
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.change_password_title),
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; errorMessage = null },
                    label = { Text(stringResource(R.string.old_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (oldPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                            Icon(icon, contentDescription = null, tint = Color.Gray)
                        }
                    }
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; errorMessage = null },
                    label = { Text(stringResource(R.string.new_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (newPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(icon, contentDescription = null, tint = Color.Gray)
                        }
                    }
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text(stringResource(R.string.confirm_new_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            val fillAllFieldsError = stringResource(R.string.fill_all_fields)
            val passwordMinLengthError = stringResource(R.string.new_password_min_length)
            val passwordsDoNotMatchError = stringResource(id = R.string.passwords_do_not_match)
            Button(
                onClick = {
                    if (oldPassword.isBlank() || newPassword.isBlank()) {
                        errorMessage = fillAllFieldsError
                        return@Button
                    }
                    if (newPassword.length < 6) {
                        errorMessage = passwordMinLengthError
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        errorMessage = passwordsDoNotMatchError
                        return@Button
                    }
                    onChangePassword(oldPassword, newPassword) { err ->
                        errorMessage = err
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.btn_save), color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.btn_cancel), color = Color.Gray)
            }
        }
    )
}

@Composable
fun AdminWorkoutSection(
    viewModel: SettingsViewModel,
    contentModifier: Modifier,
    onOpenWorkout: (DailyWorkout) -> Unit = {}
) {
    val context = LocalContext.current
    val customWorkouts by viewModel.customWorkouts
    var workoutToEdit by remember { mutableStateOf<DailyWorkout?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<DailyWorkout?>(null) }

    val workoutSavedText = stringResource(R.string.workout_saved)
    val workoutUpdatedText = stringResource(R.string.workout_updated)
    val workoutDeletedText = stringResource(R.string.workout_deleted)

    if (isCreatingNew) {
        WorkoutEditDialog(
            workoutToEdit = null,
            onDismiss = { isCreatingNew = false },
            onSave = { newWorkout ->
                viewModel.saveCustomWorkout(
                    context = context,
                    workout = newWorkout,
                    onSuccess = {
                        isCreatingNew = false
                        Toast.makeText(context, workoutSavedText, Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (workoutToEdit != null) {
        WorkoutEditDialog(
            workoutToEdit = workoutToEdit,
            onDismiss = { workoutToEdit = null },
            onSave = { updatedWorkout ->
                viewModel.saveCustomWorkout(
                    context = context,
                    workout = updatedWorkout,
                    onSuccess = {
                        workoutToEdit = null
                        Toast.makeText(context, workoutUpdatedText, Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text(stringResource(R.string.workout_delete_dialog_title), color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.workout_delete_dialog_message, rememberTranslatedTitle(workoutToDelete?.title ?: ""))) },
            confirmButton = {
                Button(
                    onClick = {
                        workoutToDelete?.let { viewModel.deleteCustomWorkout(context, it.id) }
                        workoutToDelete = null
                        Toast.makeText(context, workoutDeletedText, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(stringResource(R.string.delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            }
        )
    }

    Column(modifier = contentModifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.workout_programs_admin),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.workout_created_count, customWorkouts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Button(
                onClick = { isCreatingNew = true },
                enabled = customWorkouts.size < 20,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_program), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (customWorkouts.isEmpty()) {
            Text(stringResource(R.string.no_workouts_created), color = Color.Gray, fontSize = 13.sp)
        } else {
            customWorkouts.forEach { workout ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenWorkout(workout) },
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (!workout.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = workout.coverUrl,
                                    contentDescription = stringResource(R.string.cd_cover),
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    text = rememberTranslatedTitle(workout.title),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = stringResource(R.string.exercises_count, workout.exercises.size),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { workoutToEdit = workout }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_action), tint = Color.Yellow)
                            }
                            IconButton(onClick = { workoutToDelete = workout }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Экран просмотра программы тренировок с кнопкой назад.
 * Открывается из списка программ администратора (профиль) и показывает программу
 * так же, как пользователь видит её в своём профиле ([TrainingPlanSection]).
 */
@Composable
fun WorkoutDetailScreen(
    workout: DailyWorkout?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val contentModifier = if (isWideScreen) Modifier.fillMaxWidth().widthIn(max = 800.dp) else Modifier.fillMaxWidth()

    val fallbackTitle = stringResource(R.string.training_plan)
    val title = if (workout != null) rememberTranslatedTitle(workout.title) else fallbackTitle

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 32.dp else 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Шапка: кнопка назад + название программы на языке приложения
        Row(
            modifier = contentModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.Red
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (workout != null) {
            // Тот же вид, что и у пользователя: обложка, название, список упражнений
            TrainingPlanSection(plan = com.google.gson.Gson().toJson(workout), modifier = contentModifier)
        } else {
            // Программу не нашли (например, удалили) — пробуем подгрузить список заново
            Text(
                text = stringResource(R.string.workout_not_found),
                color = Color.Gray,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
fun WorkoutEditDialog(
    workoutToEdit: DailyWorkout?,
    onDismiss: () -> Unit,
    onSave: (DailyWorkout) -> Unit
) {
    var titleInput by remember { mutableStateOf(workoutToEdit?.title ?: "") }
    var coverUrlInput by remember { mutableStateOf(workoutToEdit?.coverUrl ?: "") }
    
    val exercises = remember {
        mutableStateListOf<Exercise>().apply {
            workoutToEdit?.exercises?.let { addAll(it) }
        }
    }

    var activeExerciseImagePickerIndex by remember { mutableStateOf<Int?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { coverUrlInput = it.toString() }
    }

    val exerciseImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val index = activeExerciseImagePickerIndex
        if (uri != null && index != null && index in exercises.indices) {
            val ex = exercises[index]
            exercises[index] = ex.copy(
                iconUrl = uri.toString(),
                tutorialImageUrl = uri.toString()
            )
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f),
        title = {
            Text(
                text = if (workoutToEdit == null) stringResource(R.string.create_workout_program) else stringResource(R.string.edit_workout_program),
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Text(stringResource(R.string.workout_step_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it; errorMessage = null },
                    label = { Text(stringResource(R.string.workout_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red)
                )

                Text(stringResource(R.string.workout_step_cover), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (coverUrlInput.isNotBlank()) {
                        AsyncImage(
                            model = coverUrlInput,
                            contentDescription = stringResource(R.string.cd_cover),
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Button(
                        onClick = { coverPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (coverUrlInput.isBlank()) stringResource(R.string.upload_cover) else stringResource(R.string.change_cover), fontSize = 12.sp)
                    }
                    if (coverUrlInput.isNotBlank()) {
                        IconButton(onClick = { coverUrlInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.delete), tint = Color.Red)
                        }
                    }
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.workout_step_exercises, exercises.size), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    
                    // Названия нового упражнения поднимаем в composable-область:
                    // stringResource нельзя вызывать внутри лямбды onClick.
                    val newExerciseName = stringResource(R.string.new_exercise)
                    val newExerciseDesc = stringResource(R.string.new_exercise_desc)
                    Button(
                        onClick = {
                            if (exercises.size < 20) {
                                exercises.add(Exercise(newExerciseName, "", newExerciseDesc, null))
                            }
                        },
                        enabled = exercises.size < 20,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (exercises.isEmpty()) {
                    Text(stringResource(R.string.add_exercise_hint), color = Color.Gray, fontSize = 12.sp)
                } else {
                    exercises.forEachIndexed { index, ex ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                            border = BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.exercise_number, index + 1), fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 12.sp)
                                    IconButton(
                                        onClick = { exercises.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.Red)
                                    }
                                }

                                OutlinedTextField(
                                    value = ex.name,
                                    onValueChange = { newName ->
                                        exercises[index] = ex.copy(name = newName)
                                    },
                                    label = { Text(stringResource(R.string.exercise_name_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = ex.desc,
                                    onValueChange = { newDesc ->
                                        exercises[index] = ex.copy(desc = newDesc)
                                    },
                                    label = { Text(stringResource(R.string.exercise_desc_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val photoUrl = ex.iconUrl.ifBlank { ex.tutorialImageUrl ?: "" }
                                    if (photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = stringResource(R.string.cd_exercise_photo),
                                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            activeExerciseImagePickerIndex = index
                                            exerciseImagePickerLauncher.launch("image/*")
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (photoUrl.isBlank()) stringResource(R.string.upload_photo_optional) else stringResource(R.string.change_photo_action),
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                    if (photoUrl.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                exercises[index] = ex.copy(iconUrl = "", tutorialImageUrl = null)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.delete), tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val workoutTitleEmptyError = stringResource(R.string.workout_title_empty_error)
            val workoutExerciseRequiredError = stringResource(R.string.workout_exercise_required)
            val workoutMaxExercisesError = stringResource(R.string.workout_max_exercises_error)
            Button(
                onClick = {
                    if (titleInput.isBlank()) {
                        errorMessage = workoutTitleEmptyError
                        return@Button
                    }
                    if (exercises.isEmpty()) {
                        errorMessage = workoutExerciseRequiredError
                        return@Button
                    }
                    if (exercises.size > 20) {
                        errorMessage = workoutMaxExercisesError
                        return@Button
                    }
                    val resultWorkout = DailyWorkout(
                        id = workoutToEdit?.id ?: UUID.randomUUID().toString(),
                        title = titleInput.trim(),
                        exercises = exercises.toList(),
                        coverUrl = coverUrlInput.ifBlank { null }
                    )
                    onSave(resultWorkout)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(stringResource(R.string.save_program), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.Gray)
            }
        }
    )
}
