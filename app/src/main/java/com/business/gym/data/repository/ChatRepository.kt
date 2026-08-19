package com.business.gym.data.repository

import android.util.Log
import com.business.gym.data.api.LocalChatMessage
import com.business.gym.data.api.LocalUser
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.dao.ChatDao
import com.business.gym.data.local.entity.ChatMessageEntity
import com.business.gym.data.local.entity.UserEntity
import com.business.gym.util.AuthUtils
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

    /**
     * Возвращает UID, пригодный для использования в API запросах.
     * Пытается найти числовой ID в локальной базе данных, если передан email.
     */
    private suspend fun getApiUid(uid: String): String {
        // Если входной параметр уже является числовым ID "1", возвращаем его как есть.
        if (uid == "1") return "1"

        if (AuthUtils.isStaticAdmin(uid)) return "1"
        
        // Попытка разрешить email -> id через локальную БД
        try {
            val user = chatDao.findUserByUidOrEmail(uid)
            if (user != null && !user.serverId.isNullOrBlank()) {
                Log.d("ChatRepository", "Resolved $uid to serverId: ${user.serverId}")
                return user.serverId
            }
        } catch (e: Exception) {
            Log.w("ChatRepository", "Error finding user in DB for ID resolution", e)
        }

        return uid
    }

    // Список UID, удаленных в текущей сессии, чтобы они не возвращались при синхронизации
    private val sessionDeletedUids = mutableSetOf<String>()

    /**
     * Помечает пользователя как удаленного в текущей сессии.
     */
    fun markUserAsDeleted(uid: String) {
        sessionDeletedUids.add(uid)
    }

    // Получение пользователей (собеседников)
    val allUsers: Flow<List<LocalUser>> = chatDao.getAllUsers().map { entities ->
        entities.map { 
            LocalUser(
                id = it.serverId,
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
            Log.d("ChatRepository", "Received ${users.size} users from VPS. Raw response data might contain avatars.")
            
            // 1. ПОЛНАЯ ОЧИСТКА КЕША перед вставкой
            chatDao.deleteAllUsers()

            // 2. Фильтруем новых пользователей
            val filteredUsers = users.filter { 
                val currentUid = it.id?.toString() ?: it.uid ?: it.email
                currentUid.isNotBlank() && !sessionDeletedUids.contains(currentUid)
            }

            val entities = filteredUsers.map { 
                val bestUid = when {
                    !it.id.isNullOrBlank() -> it.id
                    !it.uid.isNullOrBlank() -> it.uid
                    else -> it.email
                }
                
                Log.d("ChatRepository", "Mapping user: ${it.name}, avatarUrl=${it.avatarUrl}")
                UserEntity(
                    uid = bestUid,
                    serverId = it.id,
                    email = it.email, 
                    name = it.name,
                    avatarUrl = it.avatarUrl,
                    lastSeen = it.lastSeen
                )
            }
            
            chatDao.updateUsers(entities)
            Log.d("ChatRepository", "Successfully cached ${entities.size} users")
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            val apiUid = getApiUid(peerUid)
            // Кодируем UID для безопасной передачи в URL (особенно если это email с точками)
            val encodedPeer = android.net.Uri.encode(apiUid)
            Log.d("ChatRepository", "Refreshing messages for peerUid=$peerUid (apiUid=$apiUid, encoded=$encodedPeer)")
            val messages = apiService.getChatMessages(encodedPeer)
            Log.d("ChatRepository", "Received ${messages.size} messages for peer $peerUid")
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
            chatDao.updateMessages(peerUid, entities)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("ChatRepository", "Failed to refresh messages for peer=$peerUid", e)
            false
        }
    }

    suspend fun sendMessage(token: String, receiverId: String, message: String): Boolean {
        return try {
            val apiUid = getApiUid(receiverId)
            val encodedPeer = android.net.Uri.encode(apiUid)
            Log.d("ChatRepository", "Sending message to peer: $apiUid (encoded: $encodedPeer)")
            
            val response = apiService.sendChatMessage(encodedPeer, com.business.gym.data.api.MessageRequest(message))
            Log.d("ChatRepository", "Message send result: $response")
            
            // Ошибка обновления истории не должна мешать подтверждению отправки
            try {
                refreshMessages(token, receiverId)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Non-fatal: Failed to refresh messages after send", e)
            }
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
            val apiUid = getApiUid(receiverId)
            val encodedPeer = android.net.Uri.encode(apiUid)
            val textBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
            apiService.sendChatMedia(encodedPeer, textBody, filePart)
            
            // Ошибка обновления истории не должна мешать подтверждению отправки
            try {
                refreshMessages(token, receiverId)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Non-fatal: Failed to refresh media messages", e)
            }
            true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to send media message to peer=$receiverId", e)
            false
        }
    }

    suspend fun getUnreadCount(): Map<String, Int>? {
        return try {
            apiService.getUnreadCount()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to get unread count: ${e.message}")
            null
        }
    }

    suspend fun deleteChatMessages(peerUid: String): Boolean {
        var localDeleted = false
        try {
            // Пытаемся удалить в локальной БД сразу для мгновенного отклика UI
            chatDao.deleteMessagesForPeer(peerUid)
            localDeleted = true
            
            // Затем пытаемся удалить на сервере (с кодированием UID)
            val apiUid = getApiUid(peerUid)
            val encodedPeer = android.net.Uri.encode(apiUid)
            apiService.deleteChat(encodedPeer)
            return true
        } catch (e: Exception) {
            Log.e("ChatRepository", "Server deletion failed for peer=$peerUid, but local might be cleared", e)
            return localDeleted
        }
    }

    suspend fun deleteUser(uid: String): Boolean {
        try {
            Log.d("ChatRepository", "Admin action: Deleting user. UID: $uid")
            // Мы кодируем UID, так как сервер ожидает его в пути
            val apiUid = getApiUid(uid)
            val encodedUid = android.net.Uri.encode(apiUid)
            apiService.deleteUser(encodedUid)
            performLocalUserCleanup(uid)
            return true
        } catch (e: Exception) {
            // Если сервер вернул ошибку (например, 400 из-за формата), 
            // мы все равно чистим локально для корректного отображения в UI
            Log.e("ChatRepository", "Server deletion failed for $uid, forcing local cleanup", e)
            performLocalUserCleanup(uid)
            return true
        }
    }

    private suspend fun performLocalUserCleanup(uid: String) {
        chatDao.deleteUserByUid(uid)
        chatDao.deleteMessagesForPeer(uid)
        db.profileDao().deleteProfileByUid(uid)
        db.dailyNoteDao().deleteAllNotesByUid(uid)
        db.cartDao().clearCart(uid)
    }
}
