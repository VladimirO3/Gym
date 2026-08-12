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
            val response = api.getProducts()
            val entities = response.map {
                ProductEntity(it.id, it.name, it.price, it.description, it.imageUrl)
            }
            productDao.deleteAllProducts()
            productDao.insertProducts(entities)
            Log.d("ProductRepository", "Products refreshed from server: ${entities.size}")
        } catch (e: Exception) {
            Log.e("ProductRepository", "Failed to refresh products", e)
        }
    }

    suspend fun addProduct(name: RequestBody, price: RequestBody, desc: RequestBody, file: MultipartBody.Part) {
        api.addProduct(name, price, desc, file)
        refreshProducts()
    }

    suspend fun updateProduct(id: Int, name: RequestBody, price: RequestBody, desc: RequestBody, file: MultipartBody.Part?) {
        api.updateProduct(id, name, price, desc, file)
        refreshProducts()
    }

    suspend fun deleteProduct(id: Int) {
        api.deleteProduct(id)
        productDao.deleteProductById(id)
    }

    suspend fun deleteProductPhoto(id: Int) {
        api.deleteProductPhoto(id)
        refreshProducts()
    }
}
