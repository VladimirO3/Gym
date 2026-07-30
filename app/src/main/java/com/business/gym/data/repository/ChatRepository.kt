package com.business.gym.data.repository

import com.business.gym.data.api.LocalChatMessage
import com.business.gym.data.api.LocalUser
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.dao.ChatDao
import com.business.gym.data.local.entity.ChatMessageEntity
import com.business.gym.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val chatDao: ChatDao
) {
    private val apiService get() = NewsApiService.create()

    // Получение пользователей (собеседников)
    val allUsers: Flow<List<LocalUser>> = chatDao.getAllUsers().map { entities ->
        entities.map { LocalUser(uid = it.uid, email = it.email, name = it.name) }
    }

    suspend fun refreshUsers(token: String) {
        try {
            val users = apiService.getChatUsers("Bearer $token")
            val entities = users.map { 
                UserEntity(uid = it.uid, email = it.email, name = it.name) 
            }
            chatDao.deleteAllUsers()
            chatDao.insertUsers(entities)
        } catch (e: Exception) {
            // Log error
        }
    }

    // Получение сообщений
    fun getMessages(peerUid: String): Flow<List<LocalChatMessage>> = 
        chatDao.getMessagesForPeer(peerUid).map { entities ->
            entities.map { 
                LocalChatMessage(
                    id = it.id, 
                    text = it.text, 
                    senderId = it.senderId, 
                    senderName = it.senderName, 
                    timestamp = it.timestamp,
                    isRead = it.isRead
                ) 
            }
        }

    suspend fun refreshMessages(token: String, peerUid: String) {
        try {
            val messages = apiService.getChatMessages("Bearer $token", peerUid)
            val entities = messages.map { 
                ChatMessageEntity(
                    id = it.id, 
                    text = it.text, 
                    senderId = it.senderId, 
                    senderName = it.senderName, 
                    timestamp = it.timestamp,
                    peerUid = peerUid,
                    isRead = it.isRead
                ) 
            }
            chatDao.deleteMessagesForPeer(peerUid)
            chatDao.insertMessages(entities)
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun sendMessage(token: String, peerUid: String, text: String): Boolean {
        return try {
            apiService.sendChatMessage("Bearer $token", peerUid, text)
            refreshMessages(token, peerUid)
            true
        } catch (e: Exception) {
            false
        }
    }
}
