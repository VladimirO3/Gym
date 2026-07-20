package com.business.gym.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.Message
import com.business.gym.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.*

class ChatViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().getReference()
    private val auth = FirebaseAuth.getInstance()

    private val _users = mutableStateOf(listOf<UserProfile>())
    val users: State<List<UserProfile>> = _users

    private val _messages = mutableStateOf(listOf<Message>())
    val messages: State<List<Message>> = _messages

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
        if (isAdmin) {
            database.child("users").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userList = mutableListOf<UserProfile>()
                    for (child in snapshot.children) {
                        val user = child.getValue(UserProfile::class.java)
                        if (user != null && user.uid != currentUid) {
                            userList.add(user)
                        }
                    }
                    _users.value = userList
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            database.child("contacts").child(currentUid).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userList = mutableListOf<UserProfile>()
                    for (child in snapshot.children) {
                        val user = child.getValue(UserProfile::class.java)
                        if (user != null) userList.add(user)
                    }
                    
                    if (userList.isEmpty()) {
                        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                                for (uChild in userSnapshot.children) {
                                    val u = uChild.getValue(UserProfile::class.java)
                                    if (u?.email?.trim() == "+79530481451") {
                                        _users.value = listOf(u)
                                        database.child("contacts").child(currentUid).child(u.uid).setValue(u)
                                        break
                                    }
                                }
                            }
                            override fun onCancelled(e: DatabaseError) {}
                        })
                    } else {
                        _users.value = userList
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun fetchMessages(peerUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < peerUid) "${currentUid}_${peerUid}" else "${peerUid}_${currentUid}"
        
        database.child("chats").child(chatId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messageList = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val msg = child.getValue(Message::class.java)
                    if (msg != null) messageList.add(msg)
                }
                _messages.value = messageList.sortedBy { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun sendMessage(peer: UserProfile, text: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val chatId = if (currentUid < peer.uid) "${currentUid}_${peer.uid}" else "${peer.uid}_${currentUid}"
        
        val msgId = database.child("chats").child(chatId).push().key ?: UUID.randomUUID().toString()
        val message = Message(
            id = msgId,
            text = text,
            senderId = currentUid,
            recipientId = peer.uid,
            senderName = auth.currentUser?.email?.substringBefore("@") ?: "User",
            timestamp = System.currentTimeMillis()
        )
        database.child("chats").child(chatId).child(msgId).setValue(message)
        
        val currentUserProfile = UserProfile(
            uid = currentUid,
            email = auth.currentUser?.email ?: "",
            name = auth.currentUser?.email?.substringBefore("@") ?: "User"
        )
        database.child("contacts").child(peer.uid).child(currentUid).setValue(currentUserProfile)
    }
}
