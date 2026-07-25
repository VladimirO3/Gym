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

@Composable
fun ChatScreen(
    currentUid: String,
    isAdmin: Boolean,
    viewModel: ChatViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val selectedUser by viewModel.selectedUser
    
    LaunchedEffect(currentUid, isAdmin) {
        viewModel.fetchUsers(currentUid, isAdmin)
    }

    if (currentUid.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (selectedUser == null) {
        UserListScreen(
            users = viewModel.users.value,
            onUserSelected = { viewModel.selectUser(it, currentUid) },
            modifier = modifier
        )
    } else {
        ConversationScreen(
            currentUid = currentUid,
            peer = selectedUser!!,
            messages = viewModel.messages.value,
            onBack = { viewModel.selectUser(null, currentUid) },
            onSendMessage = { viewModel.sendMessage(selectedUser!!, it, currentUid) },
            modifier = modifier
        )
    }
}

@Composable
fun UserListScreen(
    users: List<UserProfile>,
    onUserSelected: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.chat_all_users),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (users.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.chat_no_one), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users) { user ->
                    val isUserAdmin = AuthViewModel.isStaticAdmin(user.email)
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onUserSelected(user) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUserAdmin) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isUserAdmin) Icons.Default.Person else Icons.Default.Person, 
                                null,
                                tint = if (isUserAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isUserAdmin) "Администратор" else user.name, 
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isUserAdmin) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (!isUserAdmin) {
                                    Text(text = user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (AuthViewModel.isStaticAdmin(peer.email)) "Администратор" else peer.name,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message, message.senderId == currentUid)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
