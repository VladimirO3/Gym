package com.business.gym.data.repository

import android.util.Log
import com.business.gym.data.api.LocalChatMessage
import com.business.gym.data.api.LocalUser
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.dao.ChatDao
import com.business.gym.data.local.entity.ChatMessageEntity
import com.business.gym.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(
    private val chatDao: ChatDao,
    private val context: android.content.Context
) {
    private val db by lazy { GymDatabase.getDatabase(context) }
    private val apiService get() = NewsApiService.create(context)

    // Получение пользователей (собеседников)
    val allUsers: Flow<List<LocalUser>> = chatDao.getAllUsers().map { entities ->
        entities.map { 
            LocalUser(
                uid = it.uid, 
                email = it.email, 
                name = it.name,
                avatarUrl = it.avatarUrl,
                lastSeen = it.lastSeen
            ) 
        }
    }

    suspend fun refreshUsers(token: String): Boolean {
        return try {
            Log.d("ChatRepository", "Refreshing chat users from VPS...")
            val users = apiService.getChatUsers()
            Log.d("ChatRepository", "Received ${users.size} users from VPS")
            val entities = users.map { 
                // Гарантируем наличие UID (если пустой, используем email)
                val finalUid = if (it.uid.isNullOrBlank()) it.email else it.uid
                UserEntity(
                    uid = finalUid, 
                    email = it.email, 
                    name = it.name,
                    avatarUrl = it.avatarUrl,
                    lastSeen = it.lastSeen
                )
            }
            chatDao.deleteAllUsers()
            chatDao.insertUsers(entities)
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "CRITICAL: Failed to refresh chat users", e)
            false
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
                    isRead = it.isRead,
                    mediaUrl = it.mediaUrl,
                    mediaType = it.mediaType
                ) 
            }
        }

    suspend fun refreshMessages(token: String, peerUid: String): Boolean {
        return try {
            val messages = apiService.getChatMessages(peerUid)
            val entities = messages.map { 
                ChatMessageEntity(
                    id = it.id, 
                    text = it.text, 
                    senderId = it.senderId, 
                    senderName = it.senderName, 
                    timestamp = it.timestamp,
                    peerUid = peerUid,
                    isRead = it.isRead,
                    mediaUrl = it.mediaUrl,
                    mediaType = it.mediaType
                ) 
            }
            chatDao.deleteMessagesForPeer(peerUid)
            chatDao.insertMessages(entities)
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to refresh messages for peer=$peerUid", e)
            false
        }
    }

    suspend fun sendMessage(token: String, receiverId: String, message: String): Boolean {
        return try {
            Log.d("ChatRepository", "Sending message via Multipart. peerUid: $receiverId, text: $message")
            
            val receiverIdBody = receiverId.toRequestBody("text/plain".toMediaTypeOrNull())
            val textBody = message.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val response = apiService.sendChatMessage(receiverIdBody, textBody)
            Log.d("ChatRepository", "Message send result: $response")
            refreshMessages(token, receiverId)
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "CRITICAL ERROR: Failed to send message to peer=$receiverId", e)
            if (e is retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("ChatRepository", "HTTP Error body: $errorBody")
            }
            false
        }
    }

    suspend fun sendMediaMessage(token: String, receiverId: String, text: String, filePart: okhttp3.MultipartBody.Part): Boolean {
        return try {
            val receiverIdBody = receiverId.toRequestBody("text/plain".toMediaTypeOrNull())
            val textBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.sendChatMedia(receiverIdBody, textBody, filePart)
            refreshMessages(token, receiverId)
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to send media message to peer=$receiverId", e)
            false
        }
    }

    suspend fun getUnreadCount(): Map<String, Int> {
        return try {
            apiService.getUnreadCount()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to get unread count", e)
            emptyMap()
        }
    }

    suspend fun deleteChatMessages(peerUid: String): Boolean {
        var localDeleted = false
        try {
            // Пытаемся удалить в локальной БД сразу для мгновенного отклика UI
            chatDao.deleteMessagesForPeer(peerUid)
            localDeleted = true
            
            // Затем пытаемся удалить на сервере
            apiService.deleteChat(peerUid)
            return true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Server deletion failed for peer=$peerUid, but local might be cleared", e)
            return localDeleted
        }
    }

    suspend fun deleteUser(uid: String): Boolean {
        try {
            Log.d("ChatRepository", "Admin action: Deleting user $uid")
            
            // 1. Отправляем запрос на сервер
            apiService.deleteUser(uid, uid)
            Log.i("ChatRepository", "VPS User deletion confirmed for $uid")

            // 2. Только после подтверждения сервера чистим локальный кэш
            chatDao.deleteUserByUid(uid)
            chatDao.deleteMessagesForPeer(uid)
            db.profileDao().deleteProfileByUid(uid)
            db.dailyNoteDao().deleteAllNotesByUid(uid)
            db.cartDao().clearCart(uid)
            
            return true
        } catch (e: Exception) {
            Log.e("ChatRepository", "CRITICAL: VPS failed to delete user $uid.", e)
            return false // Возвращаем false при ошибке сервера
        }
    }
}
