package com.business.gym.data.local.dao

import androidx.room.*
import com.business.gym.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM shop_products")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM shop_products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM shop_products WHERE id = :productId")
    suspend fun deleteProductById(productId: Int)
}
