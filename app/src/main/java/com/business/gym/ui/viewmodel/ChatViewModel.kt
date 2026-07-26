package com.business.gym.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.ChatMessage
import com.business.gym.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.business.gym.util.EncryptionUtils
import java.util.*

import com.business.gym.GymApplication
import com.business.gym.util.NotificationHelper

/**
 * ViewModel для управления чатом между пользователями и администратором.
 * Отвечает за шифрование сообщений, получение списка контактов и уведомления в реальном времени.
 */
class ChatViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Флаг для игнорирования уведомлений о старых сообщениях при первом открытии чата
    private var isFirstMessagesLoad = true

    // --- Состояния UI ---
    
    // Список доступных пользователей для чата
    private val _users = mutableStateOf(listOf<UserProfile>())
    val users: State<List<UserProfile>> = _users

    // Список сообщений в текущем выбранном чате
    private val _messages = mutableStateOf(listOf<ChatMessage>())
    val messages: State<List<ChatMessage>> = _messages

    // Выбранный в данный момент собеседник
    private val _selectedUser = mutableStateOf<UserProfile?>(null)
    val selectedUser: State<UserProfile?> = _selectedUser

    /**
     * Метод для выбора собеседника и начала загрузки сообщений.
     */
    fun selectUser(user: UserProfile?, currentUid: String) {
        _selectedUser.value = user
        if (user != null) {
            isFirstMessagesLoad = true // Сбрасываем флаг для нового чата
            fetchMessages(user.uid, currentUid)
        } else {
            // Если чат закрыт, очищаем список сообщений
            _messages.value = emptyList()
        }
    }

    // Регистраторы слушателей для корректной очистки
    private var usersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var rtdbListener: ValueEventListener? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Загрузка списка пользователей.
     * Админ видит всех клиентов, клиент видит только админа.
     */
    fun fetchUsers(currentUid: String, isAdmin: Boolean) {
        android.util.Log.d("ChatViewModel", "fetchUsers: currentUid='$currentUid', isAdmin=$isAdmin")
        
        // Очистка старых слушателей
        usersListener?.remove()
        rtdbListener?.let { database.getReference("users").removeEventListener(it) }
        
        /**
         * Обработка объединенных данных из Firestore и Realtime Database.
         */
        fun processCombinedUsers(firestoreUsers: List<UserProfile>, rtdbUsers: List<UserProfile>) {
            // Удаляем дубликаты по UID
            val allUsersMap = (firestoreUsers + rtdbUsers).filter { it.uid.isNotBlank() }.associateBy { it.uid }
            
            val regularUsers = mutableListOf<UserProfile>()
            val admins = mutableListOf<UserProfile>()

            for (user in allUsersMap.values) {
                if (user.uid == currentUid) continue // Не показываем себя в списке

                if (AuthViewModel.isStaticAdmin(user.email)) {
                    admins.add(user)
                } else {
                    regularUsers.add(user)
                }
            }

            // Сортировка по алфавиту
            regularUsers.sortBy { it.name.lowercase() }
            admins.sortBy { it.name.lowercase() }

            // Фильтрация списка в зависимости от роли
            val finalList = if (isAdmin) regularUsers else admins
            _users.value = finalList
        }

        var currentFirestoreUsers = listOf<UserProfile>()
        var currentRtdbUsers = listOf<UserProfile>()

        // Слушаем изменения в коллекции пользователей Firestore
        usersListener = firestore.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                currentFirestoreUsers = snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }
                processCombinedUsers(currentFirestoreUsers, currentRtdbUsers)
            }
        }

        // Слушаем изменения в Realtime Database (для обратной совместимости)
        rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userList = mutableListOf<UserProfile>()
                snapshot.children.forEach { child ->
                    child.getValue(UserProfile::class.java)?.let { userList.add(it) }
                }
                currentRtdbUsers = userList
                processCombinedUsers(currentFirestoreUsers, currentRtdbUsers)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.getReference("users").addValueEventListener(rtdbListener!!)
    }

    /**
     * Очистка слушателей при уничтожении ViewModel.
     */
    override fun onCleared() {
        super.onCleared()
        usersListener?.remove()
        messagesListener?.remove()
        rtdbListener?.let { database.getReference("users").removeEventListener(it) }
    }

    /**
     * Загрузка истории сообщений конкретного чата.
     */
    private fun fetchMessages(peerUid: String, currentUid: String) {
        if (currentUid.isBlank() || peerUid.isBlank()) return
        
        messagesListener?.remove()
        _messages.value = emptyList()

        // Генерация уникального ID чата: всегда "меньшийUID_большийUID"
        val chatId = if (currentUid < peerUid) "${currentUid}_${peerUid}" else "${peerUid}_${currentUid}"
        
        // Слушаем коллекцию сообщений в реальном времени
        messagesListener = firestore.collection("rooms").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                
                if (snapshot == null || snapshot.isEmpty) {
                    _messages.value = emptyList()
                    return@addSnapshotListener
                }

                // Декодирование и расшифровка сообщений
                val messageList = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    msg?.let { 
                        val decrypted = EncryptionUtils.decrypt(it.text)
                        it.copy(text = decrypted)
                    }
                }
                
                // --- ЛОГИКА УВЕДОМЛЕНИЙ В ШТОРКУ ---
                if (!isFirstMessagesLoad && messageList.size > _messages.value.size) {
                    val newMessage = messageList.last()
                    // Уведомляем только если сообщение входящее (не от нас)
                    if (newMessage.senderId != currentUid) {
                        NotificationHelper.showNotification(
                            GymApplication.instance,
                            newMessage.senderName,
                            newMessage.text
                        )
                    }
                }
                
                isFirstMessagesLoad = false
                _messages.value = messageList
            }
    }

    /**
     * Отправка зашифрованного сообщения собеседнику.
     */
    fun sendMessage(peer: UserProfile, text: String, currentUid: String) {
        if (currentUid.isBlank() || text.isBlank() || peer.uid.isBlank()) return
        
        val chatId = if (currentUid < peer.uid) "${currentUid}_${peer.uid}" else "${peer.uid}_${currentUid}"
        
        // Шифрование AES для защиты данных в облаке
        val encryptedText = EncryptionUtils.encrypt(text)
        
        // Подготовка имени отправителя
        val rawName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "User"
        val senderName = if (AuthViewModel.isStaticAdmin(auth.currentUser?.email ?: auth.currentUser?.phoneNumber)) {
            "Администратор"
        } else {
            rawName
        }
        
        // Данные сообщения
        val docData = hashMapOf(
            "text" to encryptedText,
            "senderId" to currentUid,
            "senderName" to senderName,
            "timestamp" to FieldValue.serverTimestamp()
        )

        // 1. Сохраняем само сообщение в историю
        firestore.collection("rooms").document(chatId).collection("messages").add(docData)
            .addOnSuccessListener { ref ->
                // 2. Обновляем превью в списке чатов (lastMessage)
                val roomUpdate = hashMapOf(
                    "lastMessage" to text, 
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "participants" to listOf(currentUid, peer.uid)
                )
                firestore.collection("rooms").document(chatId).set(roomUpdate, com.google.firebase.firestore.SetOptions.merge())
            }
            .addOnFailureListener { e -> 
                // Уведомляем только админа о проблемах с БД
                if (AuthViewModel.isStaticAdmin(auth.currentUser?.email ?: auth.currentUser?.phoneNumber)) {
                    NotificationHelper.showNotification(GymApplication.instance, "DB Error", e.message ?: "Unknown")
                }
            }
        
        // Фоновая проверка/обновление своего профиля
        if (auth.currentUser != null) {
            val myEmail = auth.currentUser?.email ?: ""
            val myName = auth.currentUser?.displayName ?: myEmail.substringBefore("@")
            val myProfile = UserProfile(uid = currentUid, email = myEmail, name = myName)
            firestore.collection("users").document(currentUid).set(myProfile, com.google.firebase.firestore.SetOptions.merge())
        }
    }
}
