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
import com.business.gym.util.AuthUtils
import com.business.gym.util.NotificationHelper
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

    /**
     * Полная очистка состояния чата и остановка всех процессов.
     * Должно вызываться при выходе из аккаунта.
     */
    fun clearAll() {
        stopPolling()
        stopMessagesObservation()
        globalPollingJob?.cancel()
        _selectedUser.value = null
        _messages.value = emptyList()
        _users.value = emptyList()
        _notifiedCounts.value = emptyMap()
        hasFetchedUsers = false
        deletedUserUids.clear()
        Log.d("ChatViewModel", "Chat state cleared and polling stopped")
    }

    init {
        // Подписка на список пользователей из локальной базы данных (Room)
        viewModelScope.launch {
            repository.allUsers.collect { localUsers ->
                Log.d("ChatViewModel", "Observed ${localUsers.size} users from repository")
                // Получаем текущий email для фильтрации "себя"
                val sharedPref = getApplication<com.business.gym.GymApplication>()
                    .getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                val currentEmail = sharedPref.getString("user_session_email", "") ?: ""
                val currentUidFromPrefs = sharedPref.getString("user_session_uid", "") ?: ""
                
                Log.d("ChatViewModel", "Filtering ${localUsers.size} users. Self: email=$currentEmail, uid=$currentUidFromPrefs. DeletedUids: $deletedUserUids")

                val profiles = localUsers
                    .filter { 
                        // Фильтруем "себя"
                        val isSameEmail = currentEmail.isNotBlank() && it.email.isNotBlank() && it.email.trim().lowercase() == currentEmail.trim().lowercase()
                        val isSameUid = currentUidFromPrefs.isNotBlank() && it.uid?.isNotBlank() == true && it.uid == currentUidFromPrefs
                        val isSameId = currentUidFromPrefs.isNotBlank() && it.id?.isNotBlank() == true && it.id == currentUidFromPrefs
                        
                        val isSelf = isSameEmail || isSameUid || isSameId
                        val isDeleted = deletedUserUids.contains(it.uid) || (it.id != null && deletedUserUids.contains(it.id))
                        
                        if (isSelf) Log.d("ChatViewModel", "Filtered out self: email=${it.email}, uid=${it.uid}, id=${it.id}")
                        
                        !isSelf && !isDeleted
                    }
                    .map { 
                        val isAdminVal = when(it.isAdmin) {
                            is Boolean -> it.isAdmin
                            is Number -> it.isAdmin.toInt() == 1
                            is String -> it.isAdmin.lowercase() == "true" || it.isAdmin == "1"
                            else -> false
                        }
                        val roleStr = it.role?.toString()
                        
                        // Используем имя напрямую от сервера, так как сервер теперь сам 
                        // формирует красивые имена ("root-администратор", "Имя - администратор")
                        UserProfile(
                            uid = it.id ?: it.uid ?: it.email,
                            email = it.email, 
                            name = it.name,
                            age = when(val a = it.age) {
                                is Number -> a.toInt()
                                is String -> a.toIntOrNull()
                                else -> null
                            },
                            avatarUrl = it.avatarUrl,
                            lastSeen = it.lastSeen,
                            isAdmin = isAdminVal || roleStr == "admin",
                            role = roleStr,
                            isRootAdmin = it.email.trim().lowercase() == "verso0100@gmail.com"
                        ) 
                    }
                    .toMutableList()
                
                profiles.forEach { 
                    Log.d("ChatViewModel", "Profile in list: ${it.name}, avatar=${it.avatarUrl}")
                }
                
                Log.d("ChatViewModel", "After filtering, profiles count: ${profiles.size}")
                
                // Если мы не администратор, гарантируем наличие администратора в списке
                val isMeAdmin = AuthUtils.isStaticAdmin(currentEmail)
                if (!isMeAdmin) {
                    val adminInList = profiles.find { AuthUtils.isStaticAdmin(it.email) }
                    if (adminInList != null) {
                        profiles.remove(adminInList)
                        profiles.add(0, adminInList.copy(name = "root-администратор"))
                    } else {
                        Log.d("ChatViewModel", "Admin not found in list, adding manually")
                        profiles.add(0, UserProfile(
                            uid = "1",
                            email = AuthUtils.ADMIN_EMAIL,
                            name = "root-администратор",
                            isAdmin = true,
                            role = "admin"
                        ))
                    }
                }
                
                _users.value = profiles
                Log.d("ChatViewModel", "State _users updated with ${profiles.size} items")
            }
        }
    }

    private var messagesJob: kotlinx.coroutines.Job? = null
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var globalPollingJob: kotlinx.coroutines.Job? = null
    
    // Карта для отслеживания количества непрочитанных сообщений, о которых уже уведомили
    // Key: digital UID or email, Value: count
    private val _notifiedCounts = mutableStateOf<Map<String, Int>>(emptyMap())
    val notifiedCounts: State<Map<String, Int>> = _notifiedCounts

    /**
     * Возвращает общее количество непрочитанных сообщений для всех пользователей.
     */
    fun getTotalUnreadCount(): Int {
        return _notifiedCounts.value.filter { it.key != _selectedUser.value?.uid }.values.sum()
    }

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
                    if (unreadMap != null) {
                        if (unreadMap.isNotEmpty()) {
                            val currentNotified = _notifiedCounts.value.toMutableMap()
                            var changed = false

                            unreadMap.forEach { (senderId, count) ->
                                Log.d("ChatViewModel", "Unread check: Sender=$senderId, Count=$count")
                                val lastNotifiedCount = currentNotified[senderId] ?: 0
                                
                                // Проверяем наличие пользователя в нашем списке (по UID, ServerID или Email)
                                val userInList = _users.value.find { 
                                    it.uid == senderId || it.email == senderId
                                }
                                
                                if (count > 0 && senderId != _selectedUser.value?.uid && count > lastNotifiedCount) {
                                    val senderName = userInList?.name ?: "Новое сообщение"
                                    NotificationHelper.showNotification(
                                        getApplication(),
                                        senderName,
                                        "У вас $count новых сообщений",
                                        senderId
                                    )
                                    // Сохраняем в notifiedCounts именно тот ID, который прислал сервер
                                    currentNotified[senderId] = count
                                    changed = true
                                } else if (count == 0 && currentNotified.containsKey(senderId)) {
                                    NotificationHelper.cancelNotification(getApplication(), senderId)
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
        Log.d("ChatViewModel", "selectUser: ${user?.name} (uid=${user?.uid}), currentUid=$currentUid")
        _selectedUser.value = user
        user?.let { 
            // При выборе пользователя сбрасываем его флаг уведомления
            val current = _notifiedCounts.value.toMutableMap()
            current[it.uid] = 999999 
            _notifiedCounts.value = current
            // Отменяем системное уведомление для этого пользователя
            NotificationHelper.cancelNotification(getApplication(), it.uid)
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
                            timestamp = it.timestamp,
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
                delay(4000) // Проверка каждые 4 секунды, когда чат открыт
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

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    /**
     * Принудительное обновление списка пользователей с сервера.
     */
    fun fetchLocalUsers(token: String, force: Boolean = false) {
        if (hasFetchedUsers && !force) return
        _error.value = null
        Log.d("ChatViewModel", "fetchLocalUsers called with token: ${token.take(8)}...")
        
        // Очищаем уведомления при обновлении списка пользователей
        NotificationHelper.cancelAllNotifications(getApplication())

        viewModelScope.launch {
            val success = repository.refreshUsers(token)
            if (success) {
                hasFetchedUsers = true
            } else {
                _error.value = "Ошибка подключения к чату"
                Log.e("ChatViewModel", "Failed to refresh users list from VPS")
            }
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
        Log.d("ChatViewModel", "sendLocalMessage to $peerUid: $text")
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
    fun deleteUser(context: android.content.Context, uid: String, token: String?) {
        if (token == null) {
            android.widget.Toast.makeText(context, "Ошибка: вы не авторизованы", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Мгновенно убираем из UI и добавляем в "черный список" сессии
        // Это предотвратит их повторное появление даже при обновлении списка
        deletedUserUids.add(uid)
        repository.markUserAsDeleted(uid) // Синхронизируем с репозиторием, чтобы refreshUsers их не вернул
        _users.value = _users.value.filter { it.uid != uid }
        
        viewModelScope.launch {
            // 2. Выполняем удаление на VPS и в локальном кэше через репозиторий
            val success = repository.deleteUser(uid)
            
            if (success) {
                // Если мы сейчас в диалоге с этим пользователем, закрываем его
                if (_selectedUser.value?.uid == uid) {
                    _selectedUser.value = null
                    _messages.value = emptyList()
                    stopPolling()
                }
                android.widget.Toast.makeText(context, "Пользователь удален из системы", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // Если даже репозиторий вернул false (например, полная потеря связи),
                // пользователь всё равно остается в deletedUserUids, то есть скрыт из списка.
                android.widget.Toast.makeText(context, "Ошибка связи с сервером, пользователь скрыт локально", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun makeAdmin(uid: String, email: String, context: android.content.Context) {
        viewModelScope.launch {
            val success = repository.makeAdmin(uid, email)
            if (success) {
                android.widget.Toast.makeText(context, "Права администратора выданы", android.widget.Toast.LENGTH_SHORT).show()
                val token = getApplication<GymApplication>().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE).getString("user_session_token", "") ?: ""
                fetchLocalUsers(token, force = true)
            } else {
                android.widget.Toast.makeText(context, "Ошибка при выдаче прав", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeAdmin(uid: String, email: String, context: android.content.Context) {
        viewModelScope.launch {
            val success = repository.removeAdmin(uid, email)
            if (success) {
                android.widget.Toast.makeText(context, "Права администратора отозваны", android.widget.Toast.LENGTH_SHORT).show()
                val token = getApplication<GymApplication>().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE).getString("user_session_token", "") ?: ""
                fetchLocalUsers(token, force = true)
            } else {
                android.widget.Toast.makeText(context, "Ошибка при отзыве прав", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun adminUpdateProfile(uid: String, name: String, age: Int?, context: android.content.Context) {
        viewModelScope.launch {
            val success = repository.adminUpdateProfile(uid, name, age)
            if (success) {
                android.widget.Toast.makeText(context, "Профиль обновлен", android.widget.Toast.LENGTH_SHORT).show()
                val token = getApplication<com.business.gym.GymApplication>().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE).getString("user_session_token", "") ?: ""
                fetchLocalUsers(token, force = true)
                // Если редактировали текущего выбранного, обновляем его
                if (_selectedUser.value?.uid == uid) {
                    _selectedUser.value = _selectedUser.value?.copy(name = name, age = age)
                }
            } else {
                android.widget.Toast.makeText(context, "Ошибка обновления профиля", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun adminDeleteUserPhoto(uid: String, context: android.content.Context) {
        viewModelScope.launch {
            val success = repository.adminDeleteUserPhoto(uid)
            if (success) {
                android.widget.Toast.makeText(context, "Фото пользователя удалено", android.widget.Toast.LENGTH_SHORT).show()
                val token = getApplication<com.business.gym.GymApplication>().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE).getString("user_session_token", "") ?: ""
                fetchLocalUsers(token, force = true)
                // Если удаляли у текущего выбранного, обновляем его
                if (_selectedUser.value?.uid == uid) {
                    _selectedUser.value = _selectedUser.value?.copy(avatarUrl = null)
                }
            } else {
                android.widget.Toast.makeText(context, "Ошибка удаления фото", android.widget.Toast.LENGTH_SHORT).show()
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
