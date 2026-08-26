package com.business.gym.data.repository

import android.content.Context
import android.util.Log
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.api.ProductResponse
import com.business.gym.data.local.dao.ProductDao
import com.business.gym.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import okhttp3.RequestBody

class ProductRepository(
    private val productDao: ProductDao,
    private val context: Context
) {
    private val api get() = NewsApiService.create(context)

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun refreshProducts() {
        try {
            Log.d("ProductRepository", "Requesting products from API...")
            val response = api.getProducts()
            
            if (response.isEmpty()) {
                Log.w("ProductRepository", "Server returned an EMPTY product list.")
            } else {
                Log.d("ProductRepository", "Received ${response.size} products from server")
            }

            val entities = response.map {
                Log.d("ProductRepository", "Mapping product: ID=${it.id}, Name=${it.name}, Price=${it.price}, Image=${it.imageUrl}")
                val safeId = it.id.toString() // Сохраняем ID как есть в виде строки
                ProductEntity(
                    safeId,
                    it.name ?: "Без названия", 
                    it.price ?: "0 ₽", 
                    it.description ?: "", 
                    it.imageUrl ?: ""
                )
            }
            productDao.updateData(entities)
            Log.d("ProductRepository", "Products successfully updated in DB: ${entities.size} items")
        } catch (e: Exception) {
            Log.e("ProductRepository", "CRITICAL: Failed to refresh products", e)
            if (e is retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("ProductRepository", "HTTP Error: ${e.code()}, Body: $errorBody")
            } else {
                Log.e("ProductRepository", "Generic error during refresh: ${e.message}")
            }
        }
    }

    suspend fun addProduct(name: RequestBody, price: RequestBody, desc: RequestBody, file: MultipartBody.Part) {
        api.addProduct(name, price, desc, file)
        refreshProducts()
    }

    suspend fun updateProduct(id: String, name: RequestBody, price: RequestBody, desc: RequestBody, file: MultipartBody.Part?) {
        // Принудительное преобразование в Int, чтобы избежать "5.0" в URL
        val intId = id.toDoubleOrNull()?.toInt() ?: 0
        api.updateProduct(intId, name, price, desc, file)
        refreshProducts()
    }

    suspend fun deleteProduct(id: String) {
        // Принудительное преобразование в Int, даже если это Double (например, "123.0")
        val intId = id.toDoubleOrNull()?.toInt() ?: 0
        api.deleteProduct(intId)
        productDao.deleteProductById(id)
    }

    suspend fun deleteProductPhoto(id: String) {
        api.deleteProductPhoto(id)
        refreshProducts()
    }
}
