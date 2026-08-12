package com.business.gym.data.local.entity

import androidx.room.Entity

@Entity(tableName = "cart_items", primaryKeys = ["userId", "productId"])
data class CartItemEntity(
    val userId: String,
    val productId: Int,
    val quantity: Int,
    val name: String,
    val price: String,
    val description: String,
    val imageUrl: String
)
