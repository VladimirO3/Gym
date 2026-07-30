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
import androidx.compose.material.icons.filled.Person
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

import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke

import androidx.compose.ui.graphics.Color

@Composable
fun ChatScreen(
    currentUid: String,
    isAdmin: Boolean,
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(application)
    )

    val selectedUser by viewModel.selectedUser
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val jwtToken by authViewModel.jwtToken
    
    // Загрузка пользователей (только из локального сервера)
    LaunchedEffect(currentUid, isAdmin, jwtToken) {
        if (jwtToken != null) {
            viewModel.fetchLocalUsers(jwtToken!!)
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
                        users = viewModel.users.value.filter { it.uid != currentUid },
                        onUserSelected = { viewModel.selectUser(it, currentUid, jwtToken) },
                        selectedUser = selectedUser
                    )
                }
                VerticalDivider(color = Color.DarkGray)
                Box(modifier = Modifier.weight(0.6f)) {
                    if (selectedUser != null) {
                        ConversationScreen(
                            currentUid = currentUid,
                            peer = selectedUser!!,
                            messages = viewModel.messages.value,
                            onBack = { viewModel.selectUser(null, currentUid, jwtToken) },
                            onSendMessage = { 
                                viewModel.sendLocalMessage(selectedUser!!.uid, it, jwtToken)
                            },
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
                    users = viewModel.users.value.filter { it.uid != currentUid },
                    onUserSelected = { viewModel.selectUser(it, currentUid, jwtToken) },
                    modifier = modifier
                )
            } else {
                ConversationScreen(
                    currentUid = currentUid,
                    peer = selectedUser!!,
                    messages = viewModel.messages.value,
                    onBack = { viewModel.selectUser(null, currentUid, jwtToken) },
                    onSendMessage = { 
                        viewModel.sendLocalMessage(selectedUser!!.uid, it, jwtToken)
                    },
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
    selectedUser: UserProfile? = null
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.chat_all_users),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (users.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.chat_no_one), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users) { user ->
                    val isUserAdmin = AuthViewModel.isStaticAdmin(user.email)
                    val isSelected = selectedUser?.uid == user.uid
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserSelected(user) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected -> Color.Red.copy(alpha = 0.2f)
                                isUserAdmin -> Color.DarkGray.copy(alpha = 0.5f)
                                else -> Color.Black.copy(alpha = 0.3f)
                            }
                        ),
                        border = if (isSelected) BorderStroke(1.dp, Color.Red) else BorderStroke(0.5.dp, Color.DarkGray)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person, 
                                null,
                                tint = if (isUserAdmin || isSelected) Color.Red else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isUserAdmin) "Администратор" else user.name, 
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    fontWeight = if (isUserAdmin || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                if (!isUserAdmin) {
                                    Text(
                                        text = user.email, 
                                        style = MaterialTheme.typography.bodySmall, 
                                        color = Color.Gray,
                                        maxLines = 1
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
    peer: UserProfile,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (AuthViewModel.isStaticAdmin(peer.email)) "Администратор" else peer.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        
        HorizontalDivider(color = Color.DarkGray)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message, message.senderId == currentUid)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
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
