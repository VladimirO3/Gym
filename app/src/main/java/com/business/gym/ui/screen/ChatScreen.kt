package com.business.gym.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.data.model.UserProfile
import com.business.gym.data.model.ChatMessage
import com.business.gym.ui.component.MessageBubble
import com.business.gym.ui.viewmodel.AuthViewModel
import com.business.gym.ui.viewmodel.ChatViewModel
import com.business.gym.util.NotificationHelper

import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.business.gym.data.api.NewsApiService
import com.business.gym.util.AuthUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    currentUid: String,
    isAdmin: Boolean,
    viewModel: ChatViewModel, // Принимаем экземпляр извне
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val selectedUser by viewModel.selectedUser
    val notifiedCounts by viewModel.notifiedCounts
    val chatError by viewModel.error
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val jwtToken by authViewModel.jwtToken

    if (chatError != null) {
        LaunchedEffect(chatError) {
            android.widget.Toast.makeText(context, chatError, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // Загрузка пользователей (только из локального сервера)
    LaunchedEffect(currentUid, isAdmin, jwtToken) {
        android.util.Log.d("ChatScreen", "LaunchedEffect triggered: uid=$currentUid, isAdmin=$isAdmin, hasToken=${jwtToken != null}")
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
                Box(modifier = Modifier.weight(0.4f)) {
                    UserListScreen(
                        users = viewModel.users.value, // Фильтрация "себя" уже в ViewModel
                        onUserSelected = { viewModel.selectUser(it, currentUid, jwtToken) },
                        selectedUser = selectedUser,
                        notifiedCounts = notifiedCounts,
                        isAdmin = isAdmin,
                        onDeleteUser = { viewModel.deleteUser(context, it, jwtToken) },
                        jwtToken = jwtToken
                    )
                }
                VerticalDivider(color = Color.DarkGray)
                Box(modifier = Modifier.weight(0.6f)) {
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
                    isAdmin = isAdmin,
                    onDeleteUser = { viewModel.deleteUser(context, it, jwtToken) },
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
                    modifier = modifier,
                    showBackButton = true
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
    jwtToken: String? = null
) {
    var userToDelete by remember { mutableStateOf<UserProfile?>(null) }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Удалить пользователя?") },
            text = { Text("Это действие удалит пользователя ${userToDelete?.name} и всю его переписку из базы данных.") },
            confirmButton = {
                TextButton(onClick = { 
                    userToDelete?.let { onDeleteUser(it.uid) }
                    userToDelete = null 
                }) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Отмена")
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
        Spacer(modifier = Modifier.height(16.dp))

        if (users.isEmpty()) {
            android.util.Log.d("ChatScreen", "User list is empty in UI")
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = "Тут скоро будет чат", color = Color.Gray)
            }
        } else {
            android.util.Log.d("ChatScreen", "Displaying ${users.size} users")
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                    items(users) { user ->
                        val isUserAdmin = AuthUtils.isStaticAdmin(user.email) || user.uid == "1"
                        val isSelected = selectedUser?.uid == user.uid
                        
                        // Проверяем уведомления по UID и по Email (на случай несовпадения форматов)
                        val unreadCount = notifiedCounts[user.uid] ?: notifiedCounts[user.email] ?: notifiedCounts["1"].takeIf { isUserAdmin } ?: 0
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
                                isUserAdmin -> Color.DarkGray.copy(alpha = 0.5f)
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
                                        tint = if (isUserAdmin || isSelected || hasNotification) Color.Red else Color.Gray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isUserAdmin) stringResource(R.string.auth_administrator) else user.name, 
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        fontWeight = if (isUserAdmin || isSelected || hasNotification) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    if (!isUserAdmin) {
                                        val lastSeenText = if (user.lastSeen != null && user.lastSeen > 0) {
                                            val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
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

            // Кнопка удаления для админа (нельзя удалить другого админа)
                            if (isAdmin && !isUserAdmin) {
                                IconButton(onClick = { userToDelete = user }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete, 
                                        contentDescription = "Delete User", 
                                        tint = Color.Red.copy(alpha = 0.7f), 
                                        modifier = Modifier.size(24.dp)
                                    )
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
            title = { Text("Удалить чат?") },
            text = { Text("Это действие безвозвратно удалит всю историю переписки с этим пользователем.") },
            confirmButton = {
                TextButton(onClick = { 
                    onDeleteChat()
                    showDeleteConfirm = false 
                }) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
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
            // Padding handling is done in MainActivity for the entire screen
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            
            Text(
                text = if (AuthUtils.isStaticAdmin(peer.email)) stringResource(R.string.auth_administrator) else peer.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )

            // Кнопка удаления чата справа
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
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
