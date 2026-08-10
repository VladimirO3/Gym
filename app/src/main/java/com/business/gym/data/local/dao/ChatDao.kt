package com.business.gym.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.business.gym.data.local.entity.ChatMessageEntity
import com.business.gym.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Сообщения
    @Query("SELECT * FROM chat_messages WHERE peerUid = :peerUid ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerUid: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE peerUid = :peerUid")
    suspend fun deleteMessagesForPeer(peerUid: String)

    // Пользователи (собеседники)
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
