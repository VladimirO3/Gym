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

/**
 * ViewModel для управления чатом между пользователями и администратором.
 */
class ChatViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Список доступных пользователей (контактов)
    private val _users = mutableStateOf(listOf<UserProfile>())
    val users: State<List<UserProfile>> = _users

    // Список сообщений в текущем выбранном чате
    private val _messages = mutableStateOf(listOf<ChatMessage>())
    val messages: State<List<ChatMessage>> = _messages

    // Выбранный собеседник
    private val _selectedUser = mutableStateOf<UserProfile?>(null)
    val selectedUser: State<UserProfile?> = _selectedUser

    /**
     * Выбор пользователя для начала/продолжения диалога.
     */
    fun selectUser(user: UserProfile?, currentUid: String) {
        _selectedUser.value = user
        if (user != null) {
            // Загружаем сообщения именно для этого диалога
            fetchMessages(user.uid, currentUid)
        } else {
            // Если деактивировали чат, очищаем список сообщений
            _messages.value = emptyList()
        }
    }

    private var usersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var rtdbListener: ValueEventListener? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Загрузка списка пользователей из Firestore и Realtime Database.
     */
    fun fetchUsers(currentUid: String, isAdmin: Boolean) {
        android.util.Log.d("ChatViewModel", "fetchUsers: currentUid='$currentUid', isAdmin=$isAdmin")
        
        // Очистка старых слушателей перед установкой новых
        usersListener?.remove()
        rtdbListener?.let { database.getReference("users").removeEventListener(it) }
        
        /**
         * Внутренняя функция для объединения данных из двух БД.
         */
        fun processCombinedUsers(firestoreUsers: List<UserProfile>, rtdbUsers: List<UserProfile>) {
            // Объединяем списки и удаляем дубликаты по UID
            val allUsersMap = (firestoreUsers + rtdbUsers).filter { it.uid.isNotBlank() }.associateBy { it.uid }
            
            val regularUsers = mutableListOf<UserProfile>()
            val admins = mutableListOf<UserProfile>()

            for (user in allUsersMap.values) {
                // Не показываем текущего пользователя в его же списке
                if (user.uid == currentUid) continue

                val isThisUserAdmin = AuthViewModel.isStaticAdmin(user.email)
                if (isThisUserAdmin) {
                    admins.add(user)
                } else {
                    regularUsers.add(user)
                }
            }

            // Сортировка по имени
            regularUsers.sortBy { it.name.lowercase() }
            admins.sortBy { it.name.lowercase() }

            // Если зашел админ — он видит всех клиентов. Если клиент — он видит только админов.
            val finalList = if (isAdmin) regularUsers else admins
            android.util.Log.d("ChatViewModel", "Users updated: count=${finalList.size}")
            _users.value = finalList
        }

        var currentFirestoreUsers = listOf<UserProfile>()
        var currentRtdbUsers = listOf<UserProfile>()

        // Слушатель изменений в Firestore
        usersListener = firestore.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("ChatViewModel", "Firestore Users Error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                currentFirestoreUsers = snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }
                processCombinedUsers(currentFirestoreUsers, currentRtdbUsers)
            }
        }

        // Слушатель изменений в Realtime Database
        rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userList = mutableListOf<UserProfile>()
                snapshot.children.forEach { child ->
                    child.getValue(UserProfile::class.java)?.let { userList.add(it) }
                }
                currentRtdbUsers = userList
                processCombinedUsers(currentFirestoreUsers, currentRtdbUsers)
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ChatViewModel", "RTDB Users Error: ${error.message}")
            }
        }
        database.getReference("users").addValueEventListener(rtdbListener!!)
    }

    /**
     * Очистка всех активных слушателей при уничтожении ViewModel.
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
        if (currentUid.isBlank() || peerUid.isBlank()) {
            android.util.Log.w("ChatViewModel", "fetchMessages: blank IDs")
            return
        }
        
        // Отключаем старый чат перед открытием нового
        messagesListener?.remove()
        _messages.value = emptyList()

        // Генерация уникального ID чата (всегда одинаковый для двух людей)
        val chatId = if (currentUid < peerUid) "${currentUid}_${peerUid}" else "${peerUid}_${currentUid}"
        android.util.Log.d("ChatViewModel", "Listening to chatId: $chatId")
        
        // Слушаем подколлекцию messages внутри конкретной комнаты
        messagesListener = firestore.collection("rooms").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatViewModel", "Messages Listener Error: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot == null || snapshot.isEmpty) {
                    android.util.Log.d("ChatViewModel", "No messages found for chatId: $chatId")
                    _messages.value = emptyList()
                    return@addSnapshotListener
                }

                // Преобразование документов в объекты и их расшифровка
                val messageList = snapshot.documents.mapNotNull { doc ->
                    android.util.Log.d("ChatViewModel", "Processing doc: ${doc.id}, data: ${doc.data}")
                    val msg = doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    if (msg == null) {
                        android.util.Log.e("ChatViewModel", "Failed to parse document ${doc.id} to ChatMessage")
                    }
                    msg?.let { 
                        val decrypted = EncryptionUtils.decrypt(it.text)
                        android.util.Log.d("ChatViewModel", "Decrypted message: $decrypted")
                        it.copy(text = decrypted)
                    }
                }
                _messages.value = messageList
                android.util.Log.d("ChatViewModel", "Fetched ${messageList.size} messages for chatId: $chatId")
            }
    }

    /**
     * Отправка зашифрованного сообщения.
     */
    fun sendMessage(peer: UserProfile, text: String, currentUid: String) {
        if (currentUid.isBlank() || text.isBlank() || peer.uid.isBlank()) {
            android.util.Log.e("ChatViewModel", "sendMessage: Invalid data. currentUid=$currentUid, peerUid=${peer.uid}")
            return
        }
        
        val chatId = if (currentUid < peer.uid) "${currentUid}_${peer.uid}" else "${peer.uid}_${currentUid}"
        
        // Шифрование текста перед сохранением в облако для защиты приватности
        val encryptedText = EncryptionUtils.encrypt(text)
        
        val messageData = ChatMessage(
            text = encryptedText,
            senderId = currentUid,
            senderName = (auth.currentUser?.displayName ?: auth.currentUser?.email ?: "User"),
            timestamp = null // Firestore заполнит его сам через FieldValue.serverTimestamp()
        )

        android.util.Log.d("ChatViewModel", "Sending message to chatId: $chatId from $currentUid")

        // Сохранение самого сообщения
        val docData = hashMapOf(
            "text" to encryptedText,
            "senderId" to currentUid,
            "senderName" to messageData.senderName,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("rooms").document(chatId).collection("messages").add(docData)
            .addOnSuccessListener { ref ->
                android.util.Log.d("ChatViewModel", "Message saved to Firestore with ID: ${ref.id}")
            }
            .addOnFailureListener { e -> 
                android.util.Log.e("ChatViewModel", "Firestore Save Error: ${e.message}") 
            }
        
        // Обновление информации о последнем сообщении в комнате (для списка чатов)
        val roomUpdate = hashMapOf(
            "lastMessage" to text, // Здесь можно хранить нешифрованный превью или шифрованный
            "updatedAt" to FieldValue.serverTimestamp(),
            "participants" to listOf(currentUid, peer.uid)
        )
        firestore.collection("rooms").document(chatId).set(roomUpdate, com.google.firebase.firestore.SetOptions.merge())

        // Фоновая синхронизация профилей для гарантии видимости в списках
        val myEmail = auth.currentUser?.email ?: ""
        val myName = auth.currentUser?.displayName ?: myEmail.substringBefore("@")
        val myProfile = UserProfile(uid = currentUid, email = myEmail, name = myName)
        
        firestore.collection("users").document(currentUid).set(myProfile, com.google.firebase.firestore.SetOptions.merge())
        firestore.collection("users").document(peer.uid).set(peer, com.google.firebase.firestore.SetOptions.merge())
    }
}
