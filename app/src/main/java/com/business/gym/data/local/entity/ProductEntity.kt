package com.business.gym.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_products")
data class ProductEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val price: String,
    val description: String,
    val imageUrl: String
)
