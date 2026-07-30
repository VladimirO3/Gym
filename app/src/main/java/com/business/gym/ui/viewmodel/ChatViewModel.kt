package com.business.gym.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.GymApplication
import com.business.gym.data.api.LocalChatMessage
import com.business.gym.data.api.LocalUser
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.model.ChatMessage
import com.business.gym.data.model.UserProfile
import com.business.gym.data.repository.ChatRepository
import com.business.gym.util.EncryptionUtils
import com.business.gym.util.NotificationHelper
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel для управления чатом между пользователями и администратором.
 */
class ChatViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    
    private var isFirstMessagesLoad = true

    // --- Состояния UI ---
    
    private val _users = mutableStateOf(listOf<UserProfile>())
    val users: State<List<UserProfile>> = _users

    private val _messages = mutableStateOf(listOf<ChatMessage>())
    val messages: State<List<ChatMessage>> = _messages

    private val _selectedUser = mutableStateOf<UserProfile?>(null)
    val selectedUser: State<UserProfile?> = _selectedUser

    init {
        // Подписка на локальных пользователей из SQLite
        viewModelScope.launch {
            repository.allUsers.collect { localUsers ->
                val profiles = localUsers.map { 
                    UserProfile(uid = it.uid, email = it.email, name = it.name) 
                }.toMutableList()
                
                // Всегда добавляем администратора в список, если его там нет (чтобы обычные пользователи могли ему написать)
                if (profiles.none { AuthViewModel.isStaticAdmin(it.email) }) {
                    profiles.add(0, UserProfile(
                        uid = AuthViewModel.ADMIN_EMAIL,
                        email = AuthViewModel.ADMIN_EMAIL,
                        name = "Администратор"
                    ))
                }
                
                _users.value = profiles
            }
        }
    }

    /**
     * Метод для выбора собеседника.
     */
    fun selectUser(user: UserProfile?, currentUid: String, token: String?) {
        _selectedUser.value = user
        stopPolling()
        
        if (user != null) {
            isFirstMessagesLoad = true
            
            // Загружаем сообщения из локальной БД
            viewModelScope.launch {
                repository.getMessages(user.uid).collect { localMsgs ->
                    _messages.value = localMsgs.map {
                        ChatMessage(
                            id = it.id.toString(),
                            text = try { EncryptionUtils.decrypt(it.text) } catch (e: Exception) { it.text },
                            senderId = it.senderId,
                            senderName = it.senderName,
                            timestamp = Timestamp(it.timestamp / 1000, 0),
                            isRead = it.isRead
                        )
                    }
                }
            }

            if (token != null) {
                startLocalPolling(user.uid, token)
            }
        } else {
            _messages.value = emptyList()
        }
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    private fun startLocalPolling(peerUid: String, token: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                val oldMessageCount = _messages.value.size
                repository.refreshMessages(token, peerUid)
                
                // Проверка на новые сообщения для уведомления
                if (!isFirstMessagesLoad && _messages.value.size > oldMessageCount) {
                    val lastMsg = _messages.value.last()
                    // Используем email из настроек как ID текущего пользователя для сравнения
                    val currentEmail = getApplication<GymApplication>().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        .getString("user_session_email", null)

                    if (lastMsg.senderId != currentEmail) {
                        val decryptedText = try { 
                            EncryptionUtils.decrypt(lastMsg.text) 
                        } catch (e: Exception) { 
                            lastMsg.text 
                        }
                        NotificationHelper.showNotification(
                            getApplication(), 
                            lastMsg.senderName, 
                            decryptedText
                        )
                    }
                }
                isFirstMessagesLoad = false
                delay(2000) 
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun fetchLocalUsers(token: String) {
        viewModelScope.launch {
            repository.refreshUsers(token)
        }
    }

    fun sendLocalMessage(peerUid: String, text: String, token: String?) {
        if (token == null) return
        viewModelScope.launch {
            val encrypted = EncryptionUtils.encrypt(text)
            repository.sendMessage(token, peerUid, encrypted)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = ChatRepository(database.chatDao())
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
