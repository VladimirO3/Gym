package com.business.gym_app.ui.screen

import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.business.gym_app.R
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.data.model.ChatMessage
import com.business.gym_app.data.model.UserProfile
import com.business.gym_app.ui.component.MessageBubble
import com.business.gym_app.ui.viewmodel.AuthViewModel
import com.business.gym_app.ui.viewmodel.ChatViewModel
import com.business.gym_app.ui.viewmodel.DailyWorkout
import com.business.gym_app.ui.viewmodel.SettingsViewModel
import com.business.gym_app.util.AuthUtils
import com.business.gym_app.util.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    currentUid: String,
    isAdmin: Boolean,
    viewModel: ChatViewModel, // Принимаем экземпляр извне
    authViewModel: AuthViewModel = viewModel(),
    isRootAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val selectedUser by viewModel.selectedUser
    val notifiedCounts by viewModel.notifiedCounts
    val chatError by viewModel.error
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val jwtToken by authViewModel.jwtToken

    var userForProfile by remember { mutableStateOf<UserProfile?>(null) }

    val effectiveIsAdmin = authViewModel.isAdmin()

    // Программы тренировок, созданные администратором (источник для назначения пользователю)
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(context.applicationContext as android.app.Application)
    )
    val adminPrograms by settingsViewModel.customWorkouts

    if (chatError != null) {
        LaunchedEffect(chatError) {
            android.widget.Toast.makeText(context, chatError, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    if (userForProfile != null && effectiveIsAdmin) {
        // Находим актуальные данные пользователя из списка в ViewModel, 
        // чтобы диалог обновлялся сразу после изменения прав
        val currentUserInList = viewModel.users.value.find { it.uid == userForProfile!!.uid } ?: userForProfile!!
        
        // ЗАЩИТА: Обычные админы не могут смотреть/редактировать профиль root-админа
        val isTargetRoot = currentUserInList.email.trim().lowercase() == "verso0100@gmail.com"
        val isMeRoot = authViewModel.currentUserEmail.value?.trim()?.lowercase() == "verso0100@gmail.com"
        
        if (isTargetRoot && !isMeRoot) {
            val creatorRestrictedText = stringResource(R.string.creator_profile_restricted)
            LaunchedEffect(Unit) {
                android.widget.Toast.makeText(context, creatorRestrictedText, android.widget.Toast.LENGTH_SHORT).show()
                userForProfile = null
            }
        } else {
            AdminUserProfileDialog(
                user = currentUserInList,
                isRootAdmin = isRootAdmin, // Передаем статус
                onDismiss = { userForProfile = null },
                onUpdate = { name, age ->
                    viewModel.adminUpdateProfile(currentUserInList.uid, name, age, context)
                },
                onDeletePhoto = {
                    viewModel.adminDeleteUserPhoto(currentUserInList.uid, context)
                },
                onToggleAdmin = { isCurrentlyAdmin ->
                    if (isCurrentlyAdmin) {
                        viewModel.removeAdmin(currentUserInList.uid, currentUserInList.email, context)
                    } else {
                        viewModel.makeAdmin(currentUserInList.uid, currentUserInList.email, context)
                    }
                },
                programs = adminPrograms,
                onLoadAssignedPrograms = { onLoaded ->
                    viewModel.loadAssignedProgramIds(currentUserInList.uid, onLoaded)
                },
                onAssignPrograms = { selected, onDone ->
                    viewModel.adminAssignPrograms(currentUserInList.uid, selected, context, onDone)
                },
                onDelete = {
                    viewModel.deleteUser(context, currentUserInList.uid, jwtToken)
                    userForProfile = null
                }
            )
        }
    }

    // Загрузка пользователей (только из локального сервера)
    LaunchedEffect(currentUid, effectiveIsAdmin, jwtToken) {
        android.util.Log.d("ChatScreen", "LaunchedEffect triggered: uid=$currentUid, isAdmin=$effectiveIsAdmin, hasToken=${jwtToken != null}")
        if (jwtToken != null) {
            viewModel.fetchLocalUsers(jwtToken!!, force = true)
            // При заходе в раздел чатов сбрасываем все уведомления в шторке
            NotificationHelper.cancelAllNotifications(context)
        } else if (currentUid.isNotBlank() && !AuthUtils.isStaticAdmin(currentUid)) {
             // Если токена нет, но мы не гость
             android.util.Log.w("ChatScreen", "JWT Token is null for UID: $currentUid")
        }
    }

    if (currentUid.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.Red)
        }
    } else {
        if (isLandscape) {
            // Адаптивный макет для горизонтальной ориентации (две колонки)
            Row(modifier = modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.3f)) { // Сузили список пользователей (было 0.4f)
                    UserListScreen(
                        users = viewModel.users.value,
                        onUserSelected = { viewModel.selectUser(it, currentUid, jwtToken) },
                        selectedUser = selectedUser,
                        notifiedCounts = notifiedCounts,
                        isAdmin = isRootAdmin,
                        onDeleteUser = { viewModel.deleteUser(context, it, jwtToken) },
                        onEditUser = { userForProfile = it },
                        jwtToken = jwtToken
                    )
                }
                VerticalDivider(color = Color.DarkGray)
                Box(modifier = Modifier.weight(0.7f)) { // Расширили область чата (было 0.6f)

                    if (selectedUser != null) {
                        ConversationScreen(
                            currentUid = currentUid,
                            currentUserEmail = authViewModel.currentUserEmail.value,
                            peer = selectedUser!!,
                            messages = viewModel.messages.value,
                            onBack = { viewModel.selectUser(null, currentUid, jwtToken) },
                            onSendMessage = { 
                                viewModel.sendLocalMessage(selectedUser!!.uid, it, jwtToken, context)
                            },
                            onSendMedia = { text, uri ->
                                viewModel.sendLocalMedia(selectedUser!!.uid, text, uri, jwtToken, context)
                            },
                            onDeleteChat = { viewModel.deleteChat(context, selectedUser!!.uid) },
                            onShowProfile = { userForProfile = selectedUser },
                            isAdmin = isRootAdmin, // Только Root может открывать профиль из чата
                            showBackButton = false
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.chat_select_user_hint),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        } else {
            // Стандартный макет для вертикальной ориентации
            if (selectedUser == null) {
                UserListScreen(
                    users = viewModel.users.value, // Фильтрация "себя" уже в ViewModel
                    onUserSelected = { viewModel.selectUser(it, currentUid, jwtToken) },
                    modifier = modifier,
                    notifiedCounts = notifiedCounts,
                    isAdmin = effectiveIsAdmin,
                    onDeleteUser = { viewModel.deleteUser(context, it, jwtToken) },
                    onEditUser = { userForProfile = it },
                    jwtToken = jwtToken
                )
            } else {
                ConversationScreen(
                    currentUid = currentUid,
                    currentUserEmail = authViewModel.currentUserEmail.value,
                    peer = selectedUser!!,
                    messages = viewModel.messages.value,
                    onBack = { viewModel.selectUser(null, currentUid, jwtToken) },
                    onSendMessage = { 
                        viewModel.sendLocalMessage(selectedUser!!.uid, it, jwtToken, context)
                    },
                    onSendMedia = { text, uri ->
                        viewModel.sendLocalMedia(selectedUser!!.uid, text, uri, jwtToken, context)
                    },
                    onDeleteChat = { viewModel.deleteChat(context, selectedUser!!.uid) },
                    onShowProfile = { userForProfile = selectedUser },
                    isAdmin = effectiveIsAdmin,
                    modifier = modifier,
                    showBackButton = true
                )
            }
        }
    }
}

@Composable
fun AdminUserProfileDialog(
    user: UserProfile,
    isRootAdmin: Boolean, // Кто открыл диалог
    onDismiss: () -> Unit,
    onUpdate: (String, Int?) -> Unit,
    onDeletePhoto: () -> Unit,
    onToggleAdmin: (Boolean) -> Unit,
    onDelete: () -> Unit,
    programs: List<DailyWorkout> = emptyList(),
    onLoadAssignedPrograms: (onLoaded: (Set<String>) -> Unit) -> Unit = {},
    onAssignPrograms: (selected: List<DailyWorkout>, onDone: () -> Unit) -> Unit = { _, _ -> }
) {
    var nameInput by remember { mutableStateOf(user.name) }
    var ageInput by remember { mutableStateOf(user.age?.toString() ?: "") }
    
    // Является ли "цель" рут-админом
    val isTargetRoot = AuthUtils.isRootAdmin(user.email) || user.uid == "1"
    val isTargetAdmin = isTargetRoot || user.isAdmin || user.role == "admin"
    
    val displayTitle = if (isTargetAdmin) {
        if (isTargetRoot) "root-администратор" 
        else user.name
    } else {
        "Профиль пользователя"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(displayTitle, color = Color.Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Отображение аватара пользователя для админа
                Box(
                    modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = NewsApiService.getFullUrl(LocalContext.current, user.avatarUrl),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                }
                
                // Только Root может удалять фото других админов
                if (!user.avatarUrl.isNullOrBlank() && (isRootAdmin || !isTargetAdmin)) {
                    TextButton(
                        onClick = onDeletePhoto,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.user_photo_delete), color = Color.Red, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Только Root может менять имена других админов
                if (isRootAdmin || !isTargetAdmin) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) ageInput = it },
                        label = { Text(stringResource(R.string.age_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                } else {
                    Text(stringResource(R.string.user_name, user.name), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.user_age, user.age ?: stringResource(R.string.age_not_set)), style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                val status = when {
                    isTargetRoot -> stringResource(R.string.root_administrator)
                    isTargetAdmin -> stringResource(R.string.administrator)
                    else -> stringResource(R.string.regular_user)
                }
                Text(stringResource(R.string.user_status, status), style = MaterialTheme.typography.bodyMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // ТОЛЬКО ROOT может изменять права администратора
                if (isRootAdmin && !isTargetRoot) {
                    Button(
                        onClick = { onToggleAdmin(isTargetAdmin) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isTargetAdmin) Color.Gray else Color.Blue)
                    ) {
                        Text(if (isTargetAdmin) stringResource(R.string.revoke_admin) else stringResource(R.string.grant_admin))
                    }
                } else if (isTargetRoot) {
                    Text(stringResource(R.string.root_admin_locked), color = Color.Gray, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }

                // Назначение программ тренировок (вместо автоматической программы)
                if (!isTargetRoot) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AssignProgramsButton(
                        programs = programs,
                        onLoadAssigned = onLoadAssignedPrograms,
                        onAssign = onAssignPrograms
                    )
                }

                // Только Root может удалять других пользователей/админов
                if (isRootAdmin && !isTargetRoot) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        },
        confirmButton = {
            if (isRootAdmin || !isTargetAdmin) {
                Button(onClick = { 
                    onUpdate(nameInput, ageInput.toIntOrNull())
                    onDismiss()
                }) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isRootAdmin || !isTargetAdmin) stringResource(R.string.cancel) else stringResource(R.string.close))
            }
        }
    )
}

/**
 * Кнопка «Добавить программу» / «Удалить программу» с выпадающим списком программ (множественный выбор).
 * Отмеченные программы назначаются пользователю от администратора вместо автоматической программы.
 * Если программа стоит, кнопка меняется на «Удалить программу».
 */
@Composable
private fun AssignProgramsButton(
    programs: List<DailyWorkout>,
    onLoadAssigned: (onLoaded: (Set<String>) -> Unit) -> Unit,
    onAssign: (selected: List<DailyWorkout>, onDone: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    // Отмечаем программы, которые уже назначены пользователю
    LaunchedEffect(Unit) {
        onLoadAssigned { ids ->
            selectedIds.clear()
            selectedIds.addAll(ids)
        }
    }

    val isAssigned = selectedIds.isNotEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!isAssigned) {
            // Кнопка «Добавить программу» (в стиле приложения)
            Button(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = programs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Добавить программу",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Кнопка «Удалить программу» (в стиле приложения) + кнопка редактирования выбора
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onAssign(emptyList()) {
                            selectedIds.clear()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Удалить программу",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Изменить программы", tint = Color.White)
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            if (programs.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Нет доступных программ", color = Color.Gray) },
                    onClick = { expanded = false }
                )
            } else {
                programs.forEach { program ->
                    val checked = program.id in selectedIds
                    DropdownMenuItem(
                        text = { Text(program.title, maxLines = 2) },
                        leadingIcon = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Color.Red)
                            )
                        },
                        onClick = {
                            if (checked) selectedIds.remove(program.id) else selectedIds.add(program.id)
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            if (selectedIds.isEmpty()) "Снять назначения / Автоплан" else "Назначить выбранные",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color.Red) },
                    onClick = {
                        val selected = programs.filter { it.id in selectedIds }
                        onAssign(selected) { expanded = false }
                    }
                )
            }
        }
    }
}

@Composable
fun UserListScreen(
    users: List<UserProfile>,
    onUserSelected: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
    selectedUser: UserProfile? = null,
    notifiedCounts: Map<String, Int> = emptyMap(),
    isAdmin: Boolean = false,
    onDeleteUser: (String) -> Unit = {},
    onEditUser: (UserProfile) -> Unit = {},
    jwtToken: String? = null
) {
    var userToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.email.contains(searchQuery, ignoreCase = true) 
        }
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text(stringResource(R.string.delete_user_title)) },
            text = { Text(stringResource(R.string.delete_user_message, userToDelete?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { 
                    userToDelete?.let { onDeleteUser(it.uid) }
                    userToDelete = null 
                }) {
                    Text(stringResource(R.string.delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.chat_all_users),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Поиск пользователя...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск", tint = Color.Gray) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = Color.Gray)
                    }
                }
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Red,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        if (filteredUsers.isEmpty()) {
            Log.d("ChatScreen", "User list is empty in UI")
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = if (searchQuery.isNotBlank()) "Пользователь не найден" else "Тут скоро будет чат", color = Color.Gray)
            }
        } else {
            Log.d("ChatScreen", "Displaying ${filteredUsers.size} users")
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                    items(filteredUsers) { user ->
                        val isTargetAdmin = user.isAdmin || user.role == "admin" || AuthUtils.isStaticAdmin(user.email)
                        val isSelected = selectedUser?.uid == user.uid
                        
                        // Проверяем уведомления по UID и по Email (на случай несовпадения форматов)
                        val unreadCount = notifiedCounts[user.uid] ?: notifiedCounts[user.email] ?: notifiedCounts["1"].takeIf { isTargetAdmin } ?: 0
                        val hasNotification = unreadCount > 0 && unreadCount != 999999

                    // Анимация пульсации для новых сообщений
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clickable { onUserSelected(user) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected -> Color.Red.copy(alpha = 0.2f)
                                hasNotification -> Color(0xFF5A1010) // Более насыщенный красный фон
                                isTargetAdmin -> Color.DarkGray.copy(alpha = 0.5f)
                                else -> Color.Black.copy(alpha = 0.3f)
                            }
                        ),
                        border = when {
                            isSelected -> BorderStroke(2.dp, Color.Red)
                            hasNotification -> BorderStroke(3.dp, Color.Red.copy(alpha = pulseAlpha)) // Толстая пульсирующая рамка
                            else -> BorderStroke(0.5.dp, Color.DarkGray)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp), 
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val currentContext = LocalContext.current
                                val avatarUrl = user.avatarUrl
                                val fullAvatarUrl = NewsApiService.getFullUrl(currentContext, avatarUrl)
                                
                                if (!avatarUrl.isNullOrBlank()) {
                                    android.util.Log.d("ChatScreen", "Loading avatar for ${user.name}: $fullAvatarUrl")
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(fullAvatarUrl)
                                            .setHeader("Authorization", "Bearer $jwtToken")
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        error = rememberVectorPainter(Icons.Default.Person),
                                        placeholder = rememberVectorPainter(Icons.Default.Person),
                                        onError = { state ->
                                            android.util.Log.e("ChatScreen", "Failed to load avatar for ${user.name}: ${state.result.throwable.message}")
                                        },
                                        onSuccess = {
                                            android.util.Log.d("ChatScreen", "Successfully loaded avatar for ${user.name}")
                                        }
                                    )
                                } else {
                                    android.util.Log.d("ChatScreen", "No avatar for ${user.name}, showing placeholder")
                                    Icon(
                                        Icons.Default.Person, 
                                        null,
                                        tint = if (isTargetAdmin || isSelected || hasNotification) Color.Red else Color.Gray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = user.name, 
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isTargetAdmin) Color.Red else Color.White,
                                        fontWeight = if (isTargetAdmin || isSelected || hasNotification) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    if (!isTargetAdmin) {
                                        val lastSeenText = if (user.lastSeen != null && user.lastSeen > 0) {
                                            val sdf = SimpleDateFormat("dd.MM HH:mm", LocalConfiguration.current.locales[0])
                                            "был(а) в сети ${sdf.format(Date(user.lastSeen))}"
                                        } else {
                                            user.email
                                        }
                                        
                                        Text(
                                            text = lastSeenText,
                                            style = MaterialTheme.typography.bodySmall, 
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            
                            if (hasNotification) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Badge(
                                        containerColor = Color.Red,
                                        contentColor = Color.White
                                    ) {
                                        Text(if (unreadCount > 99) "99+" else "$unreadCount")
                                    }
                                    Text(
                                        "НОВОЕ", 
                                        color = Color.Yellow, 
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Row {
                                if (isAdmin && !isTargetAdmin) {
                                    IconButton(onClick = { onEditUser(user) }) {
                                        Icon(
                                            imageVector = Icons.Default.Settings, 
                                            contentDescription = "Edit Profile", 
                                            tint = Color.Gray, 
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    IconButton(onClick = { userToDelete = user }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete, 
                                            contentDescription = "Delete User", 
                                            tint = Color.Red.copy(alpha = 0.7f), 
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else if (isAdmin && isTargetAdmin) {
                                     // Для других админов root-админ отображается без кнопок управления, 
                                     // но они могут нажать на него, чтобы открыть чат
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(
    currentUid: String,
    currentUserEmail: String?,
    peer: UserProfile,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendMedia: (String, Uri) -> Unit, // Новый параметр
    onDeleteChat: () -> Unit,
    onShowProfile: () -> Unit = {},
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onSendMedia(text, it) }
        text = ""
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text(stringResource(R.string.delete_chat_message)) },
            confirmButton = {
                TextButton(onClick = { 
                    onDeleteChat()
                    showDeleteConfirm = false 
                }) {
                    Text(stringResource(R.string.delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        // УЛУЧШЕННЫЙ ЗАГОЛОВОК (Исправление кнопки Назад)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { if (isAdmin) onShowProfile() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (isAdmin) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Settings, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // Кнопка удаления чата справа
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Chat", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
        
        HorizontalDivider(color = Color.DarkGray)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message, currentUid, currentUserEmail)
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp), // Максимально низко
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = { mediaPickerLauncher.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, "Attach Media", tint = Color.Gray)
                }

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_hint), color = Color.Gray) },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSendMessage(text)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.Red,
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
