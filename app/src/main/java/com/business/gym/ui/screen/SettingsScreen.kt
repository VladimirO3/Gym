package com.business.gym.ui.screen

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
import com.business.gym.R
import com.business.gym.ui.viewmodel.SettingsViewModel
import com.business.gym.ui.viewmodel.AuthViewModel

import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import com.business.gym.data.api.NewsApiService
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun SettingsScreen(
    currentUserEmail: String?,
    onLogout: () -> Unit,
    onGoToCart: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val contentModifier = if (isWideScreen) Modifier.width(600.dp) else Modifier.fillMaxWidth()
    
    val themeMode by viewModel.themeMode
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }
    val isGuest by authViewModel.isGuest
    
    val userName by viewModel.userName
    val userAge by viewModel.userAge
    val avatarUrl by viewModel.avatarUrl
    val isUpdating by viewModel.isUpdatingProfile
    val jwtToken by authViewModel.jwtToken

    var nameInput by remember { mutableStateOf(userName) }
    var ageInput by remember { mutableStateOf(userAge?.toString() ?: "") }
    var isEditMode by remember { mutableStateOf(false) }
    val canEditProfile = currentUserEmail != null && !isAdmin && !isGuest

    LaunchedEffect(userName, userAge) {
        nameInput = userName
        ageInput = userAge?.toString() ?: ""
        if (userName.isNotBlank()) {
            isEditMode = false
        }
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ЗАГОЛОВОК С ИНДИКАТОРОМ
        Box(modifier = contentModifier.fillMaxWidth()) {
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
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (canEditProfile) {
            // Блок профиля
            Text(
                text = "Профиль",
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
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                            .then(if (isEditMode || userName.isBlank()) Modifier.clickable { photoPickerLauncher.launch("image/*") } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = NewsApiService.getFullUrl(context, avatarUrl),
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
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
                            CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(100.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isEditMode && userName.isNotBlank()) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (userAge != null) {
                            Text(
                                text = "Возраст: $userAge",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(onClick = { isEditMode = true }) {
                            Text("Редактировать профиль", color = Color.Gray, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = onGoToCart,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Моя корзина", color = Color.White, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Имя") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) ageInput = it },
                            label = { Text("Возраст") },
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
                                    Text("Отмена")
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
                                Text("Сохранить", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // --- КАЛЕНДАРЬ ЗАМЕТОК ---
            Text(
                text = "Мои тренировки и заметки",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
                modifier = contentModifier,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            GymCalendar(viewModel, contentModifier)

            Spacer(modifier = Modifier.height(32.dp))

            // --- ПЛАН ТРЕНИРОВОК ДЛЯ НОВИЧКОВ ---
            TrainingPlanSection(contentModifier)

            Spacer(modifier = Modifier.height(24.dp))
        }
        
        Text(
            text = stringResource(R.string.settings_theme_mode), 
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red,
            modifier = contentModifier,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(modifier = contentModifier, horizontalAlignment = Alignment.Start) {
            ThemeOption("light", themeMode, stringResource(R.string.theme_light)) { viewModel.setThemeMode(context, currentUserEmail, it) }
            ThemeOption("dark", themeMode, stringResource(R.string.theme_dark)) { viewModel.setThemeMode(context, currentUserEmail, it) }
            ThemeOption("system", themeMode, stringResource(R.string.theme_system)) { viewModel.setThemeMode(context, currentUserEmail, it) }
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

        if (currentUserEmail != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onLogout,
                modifier = contentModifier.height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auth_logout), color = Color.White)
            }
        }

        if (isAdmin) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Заявки на регистрацию (Admin)", 
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
                modifier = contentModifier,
                textAlign = TextAlign.Center
            )
            
            val pendingUsers by authViewModel.pendingUsers
            LaunchedEffect(Unit) {
                authViewModel.fetchPendingUsers()
            }

            if (pendingUsers.isEmpty()) {
                Text(
                    "Нет новых заявок", 
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
                            Button(
                                onClick = { authViewModel.approveUser(user.uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Одобрить", color = Color.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (currentUserEmail != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Вы вошли как: $currentUserEmail", 
                style = MaterialTheme.typography.bodyMedium, 
                color = Color.Gray,
                modifier = contentModifier,
                textAlign = TextAlign.Center
            )
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
            title = { Text("Заметка на ${selectedDate}") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("Введите текст заметки...") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveNote(selectedDate, noteText)
                    showNoteDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.deleteNote(selectedDate)
                    showNoteDialog = false 
                }) { Text("Удалить", color = Color.Red) }
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
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))} ${currentMonth.year}",
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
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
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
                        text = "Заметка: $selectedNote",
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
fun TrainingPlanSection(modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = Color.Red)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "ПЛАН ТРЕНИРОВОК НА ДЕНЬ",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Red,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Text(
                text = "Программа для новичков (Full Body)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            val exercises = listOf(
                "1. Приседания с собственным весом" to "3 подхода по 15-20 раз",
                "2. Отжимания от пола (или с колен)" to "3 подхода по 10-12 раз",
                "3. Выпады на месте" to "3 подхода по 10 раз на каждую ногу",
                "4. Планка на локтях" to "3 подхода по 30-45 секунд",
                "5. Гиперэкстензия (или 'Лодочка')" to "3 подхода по 15 раз",
                "6. Скручивания на пресс" to "3 подхода по 20 раз"
            )

            exercises.forEach { (name, detail) ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(text = name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = detail, color = Color.Red.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "💡 Отдых между подходами: 60-90 секунд. Не забывайте про разминку перед началом!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun ThemeOption(mode: String, currentMode: String, label: String, onClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().clickable { onClick(mode) }.padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = currentMode == mode, 
            onClick = { onClick(mode) },
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Red, 
                unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
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
