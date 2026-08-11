package com.business.gym.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.business.gym.GymApplication
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.model.ChatMessage
import com.business.gym.data.model.UserProfile
import com.business.gym.data.repository.ChatRepository
import com.business.gym.util.NotificationHelper
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ViewModel для управления чатом между пользователями и администратором.
 * Поддерживает обмен сообщениями в реальном времени (через поллинг) и систему уведомлений.
 */
class ChatViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {
    private var isFirstMessagesLoad = true

    /**
     * Декодирование сообщения для отображения (зарезервировано для шифрования в будущем).
     */
    private fun decodeMessageForUi(raw: String): String {
        return raw
    }

    // --- Состояния UI ---
    
    // Список всех доступных собеседников
    private val _users = mutableStateOf(listOf<UserProfile>())
    val users: State<List<UserProfile>> = _users
    
    private var hasFetchedUsers = false

    // История сообщений с выбранным пользователем
    private val _messages = mutableStateOf(listOf<ChatMessage>())
    val messages: State<List<ChatMessage>> = _messages

    // Список локально удаленных пользователей (safeguard)
    private val deletedUserUids = mutableSetOf<String>()

    // Выбранный в данный момент собеседник
    private val _selectedUser = mutableStateOf<UserProfile?>(null)
    val selectedUser: State<UserProfile?> = _selectedUser

    init {
        // Подписка на список пользователей из локальной базы данных (Room)
        viewModelScope.launch {
            repository.allUsers.collect { localUsers ->
                // Получаем текущий email для фильтрации "себя"
                val currentEmail = getApplication<com.business.gym.GymApplication>()
                    .getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("user_session_email", "") ?: ""

                val profiles = localUsers
                    .filter { 
                        val isSelf = it.email.trim().lowercase() == currentEmail.trim().lowercase()
                        !isSelf && !deletedUserUids.contains(it.uid)
                    }
                    .map { UserProfile(uid = it.uid, email = it.email, name = it.name) }
                    .toMutableList()
                
                // Если мы не администратор, гарантируем наличие администратора в списке
                if (!AuthViewModel.isStaticAdmin(currentEmail)) {
                    val adminInList = profiles.find { AuthViewModel.isStaticAdmin(it.email) }
                    if (adminInList != null) {
                        // Если админ уже есть в списке от сервера, убеждаемся что он в начале и с красивым именем
                        profiles.remove(adminInList)
                        profiles.add(0, adminInList.copy(name = "Администратор"))
                    } else {
                        // Если админа нет в списке от сервера, добавляем вручную
                        profiles.add(0, UserProfile(
                            uid = "admin_static_id", // Используем фиксированный ID если админа нет в БД
                            email = AuthViewModel.ADMIN_EMAIL,
                            name = "Администратор"
                        ))
                    }
                }
                
                _users.value = profiles
            }
        }
    }

    private var messagesJob: kotlinx.coroutines.Job? = null
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var globalPollingJob: kotlinx.coroutines.Job? = null
    
    // Карта для отслеживания количества непрочитанных сообщений, о которых уже уведомили
    // Key: senderId, Value: count
    private val _notifiedCounts = mutableStateOf<Map<String, Int>>(emptyMap())
    val notifiedCounts: State<Map<String, Int>> = _notifiedCounts

    /**
     * Запускает глобальный опрос непрочитанных сообщений для фоновых уведомлений.
     */
    fun startGlobalNotificationPolling(token: String?) {
        if (token == null || token == "guest_token") return
        
        globalPollingJob?.cancel()
        globalPollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val unreadMap = repository.getUnreadCount()
                    if (unreadMap.isNotEmpty()) {
                        val currentNotified = _notifiedCounts.value.toMutableMap()
                        var changed = false

                        unreadMap.forEach { (senderId, count) ->
                            val lastNotifiedCount = currentNotified[senderId] ?: 0
                            
                            if (count > 0 && senderId != _selectedUser.value?.uid && count > lastNotifiedCount) {
                                val senderName = _users.value.find { it.uid == senderId }?.name ?: "Новое сообщение"
                                NotificationHelper.showNotification(
                                    getApplication(),
                                    senderName,
                                    "У вас $count новых сообщений",
                                    senderId
                                )
                                currentNotified[senderId] = count
                                changed = true
                            } else if (count == 0 && currentNotified.containsKey(senderId)) {
                                currentNotified.remove(senderId)
                                changed = true
                            }
                        }
                        if (changed) {
                            _notifiedCounts.value = currentNotified
                        }
                    } else if (_notifiedCounts.value.isNotEmpty()) {
                        _notifiedCounts.value = emptyMap()
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Global polling failed", e)
                }
                delay(10000) // Проверка каждые 10 секунд
            }
        }
    }

    /**
     * Сброс счетчика уведомлений для конкретного пользователя (например, при открытии чата).
     */
    fun clearNotificationFlag(senderId: String) {
        if (_notifiedCounts.value.containsKey(senderId)) {
            val current = _notifiedCounts.value.toMutableMap()
            current.remove(senderId)
            _notifiedCounts.value = current
        }
    }

    /**
     * Выбор пользователя для начала переписки.
     */
    fun selectUser(user: UserProfile?, currentUid: String, token: String?) {
        _selectedUser.value = user
        user?.let { 
            // При выборе пользователя сбрасываем его флаг уведомления
            val current = _notifiedCounts.value.toMutableMap()
            current[it.uid] = 999999 
            _notifiedCounts.value = current
        }
        stopPolling()
        stopMessagesObservation()
        
        if (user != null) {
            isFirstMessagesLoad = true
            
            // Подписка на сообщения из Room для мгновенного отображения из кэша
            messagesJob = viewModelScope.launch {
                repository.getMessages(user.uid).collect { localMsgs ->
                    _messages.value = localMsgs.map {
                        ChatMessage(
                            id = it.id.toString(),
                            text = decodeMessageForUi(it.text),
                            senderId = it.senderId,
                            senderName = it.senderName,
                            timestamp = Timestamp(it.timestamp / 1000, 0),
                            isRead = it.isRead,
                            mediaUrl = it.mediaUrl,
                            mediaType = it.mediaType
                        )
                    }
                }
            }

            if (token != null) {
                // Первый запрос к API для актуализации истории
                viewModelScope.launch {
                    repository.refreshMessages(token, user.uid)
                }
                // Запуск частого поллинга внутри активного чата
                startLocalPolling(user.uid, token)
            }
        } else {
            _messages.value = emptyList()
        }
    }

    /**
     * Локальный опрос новых сообщений внутри открытого диалога.
     */
    private fun startLocalPolling(peerUid: String, token: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                repository.refreshMessages(token, peerUid)
                delay(2000) // Проверка каждые 2 секунды, когда чат открыт
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun stopMessagesObservation() {
        messagesJob?.cancel()
        messagesJob = null
    }

    /**
     * Принудительное обновление списка пользователей с сервера.
     */
    fun fetchLocalUsers(token: String, force: Boolean = false) {
        if (hasFetchedUsers && !force) return
        viewModelScope.launch {
            repository.refreshUsers(token)
            hasFetchedUsers = true
        }
    }

    /**
     * Отправка нового сообщения.
     */
    fun sendLocalMessage(peerUid: String, text: String, token: String?, context: android.content.Context) {
        if (token == null) {
            android.widget.Toast.makeText(context, "Ошибка: Вы не авторизованы", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            val success = repository.sendMessage(token, peerUid, text)
            if (!success) {
                android.widget.Toast.makeText(context, "Не удалось отправить сообщение. Проверьте подключение к серверу.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun sendLocalMedia(peerUid: String, text: String, uri: android.net.Uri, token: String?, context: android.content.Context) {
        if (token == null) return
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val mediaType = mimeType.toMediaTypeOrNull()
                    val requestFile = bytes.toRequestBody(mediaType)
                    val filePart = okhttp3.MultipartBody.Part.createFormData("file", "media_file", requestFile)
                    
                    val success = repository.sendMediaMessage(token, peerUid, text, filePart)
                    if (!success) {
                        android.widget.Toast.makeText(context, "Ошибка при отправке медиа", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to send media", e)
            }
        }
    }

    /**
     * Удаление всей истории переписки с конкретным пользователем.
     */
    fun deleteChat(context: android.content.Context, peerUid: String) {
        viewModelScope.launch {
            val success = repository.deleteChatMessages(peerUid)
            if (success) {
                // Если мы сейчас в этом чате, выходим из него
                if (_selectedUser.value?.uid == peerUid) {
                    _selectedUser.value = null
                    _messages.value = emptyList()
                    stopPolling()
                }
                android.widget.Toast.makeText(context, "История переписки удалена", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Ошибка при удалении истории", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Административная функция: Удаление пользователя из системы.
     */
    fun deleteUser(context: android.content.Context, uid: String) {
        deletedUserUids.add(uid)
        // Мгновенно убираем пользователя из UI
        _users.value = _users.value.filter { it.uid != uid }
        
        viewModelScope.launch {
            val success = repository.deleteUser(uid)
            if (success) {
                // Если мы сейчас в чате с удаленным пользователем, выходим
                if (_selectedUser.value?.uid == uid) {
                    _selectedUser.value = null
                    _messages.value = emptyList()
                    stopPolling()
                }
                android.widget.Toast.makeText(context, "Пользователь удален", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // Если ошибка, возвращаем в список (опционально, зависит от желаемого поведения)
                // deletedUserUids.remove(uid)
                android.widget.Toast.makeText(context, "Ошибка при удалении пользователя на сервере", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        stopMessagesObservation()
        globalPollingJob?.cancel()
    }

    /**
     * Фабрика для создания ChatViewModel с внедрением зависимостей (репозитория).
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = ChatRepository(database.chatDao(), application)
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
