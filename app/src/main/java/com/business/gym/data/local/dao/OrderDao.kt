package com.business.gym.data.local.dao

import androidx.room.*
import com.business.gym.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM user_orders WHERE userId = :userId ORDER BY createdAt DESC")
    fun getUserOrders(userId: String): Flow<List<OrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Query("DELETE FROM user_orders WHERE userId = :userId")
    suspend fun clearUserHistory(userId: String)
}
