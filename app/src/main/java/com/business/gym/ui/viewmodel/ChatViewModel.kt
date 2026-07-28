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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
    private val firestore = FirebaseFirestore.getInstance()
    private val database = FirebaseDatabase.getInstance()
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
                // Если мы используем локальный сервер, отображаем пользователей оттуда
                _users.value = localUsers.map { 
                    UserProfile(uid = it.uid, email = it.email, name = it.name) 
                }
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
            
            // Загружаем сообщения из локальной БД + опрос сервера (если есть токен)
            viewModelScope.launch {
                repository.getMessages(user.uid).collect { localMsgs ->
                    _messages.value = localMsgs.map {
                        ChatMessage(
                            id = it.id.toString(),
                            text = try { EncryptionUtils.decrypt(it.text) } catch (e: Exception) { it.text },
                            senderId = it.senderId,
                            senderName = it.senderName,
                            timestamp = Timestamp(it.timestamp / 1000, 0)
                        )
                    }
                }
            }

            if (token != null) {
                startLocalPolling(user.uid, token)
            } else {
                // Если токена нет, пробуем Firebase для совместимости
                fetchMessages(user.uid, currentUid)
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
                repository.refreshMessages(token, peerUid)
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

    // --- Firebase Logic (Оставлена для совместимости) ---

    private var usersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var rtdbListener: ValueEventListener? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun fetchUsers(currentUid: String, isAdmin: Boolean) {
        usersListener?.remove()
        rtdbListener?.let { database.getReference("users").removeEventListener(it) }
        
        fun processCombinedUsers(firestoreUsers: List<UserProfile>, rtdbUsers: List<UserProfile>) {
            val allUsersMap = (firestoreUsers + rtdbUsers).filter { it.uid.isNotBlank() }.associateBy { it.uid }
            val regularUsers = mutableListOf<UserProfile>()
            val admins = mutableListOf<UserProfile>()

            for (user in allUsersMap.values) {
                if (user.uid == currentUid) continue
                if (AuthViewModel.isStaticAdmin(user.email)) admins.add(user) else regularUsers.add(user)
            }

            regularUsers.sortBy { it.name.lowercase() }
            admins.sortBy { it.name.lowercase() }
            _users.value = if (isAdmin) regularUsers else admins
        }

        usersListener = firestore.collection("users").addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                val currentFirestoreUsers = snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }
                processCombinedUsers(currentFirestoreUsers, emptyList())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        usersListener?.remove()
        messagesListener?.remove()
        rtdbListener?.let { database.getReference("users").removeEventListener(it) }
    }

    private fun fetchMessages(peerUid: String, currentUid: String) {
        if (currentUid.isBlank() || peerUid.isBlank()) return
        messagesListener?.remove()
        val chatId = if (currentUid < peerUid) "${currentUid}_${peerUid}" else "${peerUid}_${currentUid}"
        
        messagesListener = firestore.collection("rooms").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                
                val messageList = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    msg?.let { it.copy(text = EncryptionUtils.decrypt(it.text)) }
                }
                
                if (!isFirstMessagesLoad && messageList.size > _messages.value.size) {
                    val newMessage = messageList.last()
                    if (newMessage.senderId != currentUid) {
                        NotificationHelper.showNotification(GymApplication.instance, newMessage.senderName, newMessage.text)
                    }
                }
                isFirstMessagesLoad = false
                _messages.value = messageList
            }
    }

    fun sendMessage(peer: UserProfile, text: String, currentUid: String) {
        if (currentUid.isBlank() || text.isBlank() || peer.uid.isBlank()) return
        val chatId = if (currentUid < peer.uid) "${currentUid}_${peer.uid}" else "${peer.uid}_${currentUid}"
        val encryptedText = EncryptionUtils.encrypt(text)
        val rawName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "User"
        val senderName = if (AuthViewModel.isStaticAdmin(auth.currentUser?.email)) "Администратор" else rawName
        
        val docData = hashMapOf(
            "text" to encryptedText,
            "senderId" to currentUid,
            "senderName" to senderName,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("rooms").document(chatId).collection("messages").add(docData)
            .addOnSuccessListener {
                val roomUpdate = hashMapOf(
                    "lastMessage" to text, 
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "participants" to listOf(currentUid, peer.uid)
                )
                firestore.collection("rooms").document(chatId).set(roomUpdate, SetOptions.merge())
            }
            
        if (auth.currentUser != null) {
            val myProfile = UserProfile(uid = currentUid, email = auth.currentUser?.email ?: "", name = auth.currentUser?.displayName ?: "")
            firestore.collection("users").document(currentUid).set(myProfile, SetOptions.merge())
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = ChatRepository(NewsApiService.create(), database.chatDao())
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
