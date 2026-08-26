package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_orders")
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val userId: String,
    val totalPrice: Int,
    val status: String,
    val createdAt: Long,
    val itemsJson: String // Храним список товаров в формате JSON
)
