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
import java.util.*

class ChatViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _users = mutableStateOf(listOf<UserProfile>())
    val users: State<List<UserProfile>> = _users

    private val _messages = mutableStateOf(listOf<ChatMessage>())
    val messages: State<List<ChatMessage>> = _messages

    private val _selectedUser = mutableStateOf<UserProfile?>(null)
    val selectedUser: State<UserProfile?> = _selectedUser

    fun selectUser(user: UserProfile?) {
        _selectedUser.value = user
        if (user != null) {
            fetchMessages(user.uid)
        } else {
            _messages.value = emptyList()
        }
    }

    fun fetchUsers(currentUid: String, isAdmin: Boolean) {
        android.util.Log.d("ChatViewModel", "Fetching users for currentUid: $currentUid")
        
        // Listen to the entire 'users' collection
        firestore.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("ChatViewModel", "Firestore Error: ${error.message}", error)
                return@addSnapshotListener
            }

            if (snapshot == null) {
                android.util.Log.d("ChatViewModel", "Snapshot is null")
                return@addSnapshotListener
            }

            android.util.Log.d("ChatViewModel", "Snapshot received. Total documents in 'users' collection: ${snapshot.size()}")
            
            val userList = mutableListOf<UserProfile>()
            var adminUser: UserProfile? = null

            for (doc in snapshot.documents) {
                val user = doc.toObject(UserProfile::class.java)
                if (user != null) {
                    android.util.Log.d("ChatViewModel", "Checking user in DB: email=${user.email}, uid=${user.uid}")
                    
                    if (user.uid == currentUid) {
                        android.util.Log.d("ChatViewModel", "-> This is the current user. Skipping from list.")
                        continue
                    }

                    val userEmailClean = user.email.trim().lowercase()
                    val isAdminAccount = userEmailClean == AuthViewModel.ADMIN_EMAIL.lowercase() || 
                                       user.email.trim() == AuthViewModel.ADMIN_PHONE

                    if (isAdminAccount) {
                        adminUser = user
                        android.util.Log.d("ChatViewModel", "-> Admin found: ${user.email}")
                    } else {
                        userList.add(user)
                        android.util.Log.d("ChatViewModel", "-> Regular user added to list: ${user.email}")
                    }
                } else {
                    android.util.Log.w("ChatViewModel", "Failed to parse document: ${doc.id}")
                }
            }

            // Sort regular users by name
            userList.sortBy { it.name.lowercase() }

            // Combine: Admin at top, then others
            val finalList = mutableListOf<UserProfile>()
            adminUser?.let { finalList.add(it) }
            finalList.addAll(userList)

            android.util.Log.d("ChatViewModel", "Final list prepared with ${finalList.size} users. Admin present: ${adminUser != null}")
            _users.value = finalList
        }
    }

    private fun fetchMessages(peerUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < peerUid) "${currentUid}_${peerUid}" else "${peerUid}_${currentUid}"
        
        firestore.collection("rooms").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val messageList = mutableListOf<ChatMessage>()
                snapshot?.documents?.forEach { doc ->
                    val msg = doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    if (msg != null) messageList.add(msg)
                }
                _messages.value = messageList
            }
    }

    fun sendMessage(peer: UserProfile, text: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < peer.uid) "${currentUid}_${peer.uid}" else "${peer.uid}_${currentUid}"
        
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to currentUid,
            "senderName" to (auth.currentUser?.email?.substringBefore("@") ?: "User"),
            "timestamp" to FieldValue.serverTimestamp()
        )

        // Add to messages sub-collection
        firestore.collection("rooms").document(chatId).collection("messages").add(messageData)
        
        // Update room info
        val roomInfo = hashMapOf(
            "lastMessage" to text,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("rooms").document(chatId).set(roomInfo, com.google.firebase.firestore.SetOptions.merge())

        // Ensure both users have each other in contacts
        val currentUserProfile = UserProfile(
            uid = currentUid,
            email = auth.currentUser?.email ?: "",
            name = auth.currentUser?.email?.substringBefore("@") ?: "User"
        )
        firestore.collection("users").document(peer.uid).collection("contacts").document(currentUid).set(currentUserProfile)
        firestore.collection("users").document(currentUid).collection("contacts").document(peer.uid).set(peer)
    }
}
