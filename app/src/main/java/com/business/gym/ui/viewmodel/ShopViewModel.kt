package com.business.gym.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.ProductResponse
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.repository.ProductRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ViewModel для управления товарами в магазине.
 * Обрабатывает получение списка товаров с сервера, локальное кэширование и административные функции.
 */
class ShopViewModel(
    application: Application,
    private val repository: ProductRepository
) : AndroidViewModel(application) {
    
    // Список товаров из локального кэша
    private val _products = mutableStateOf<List<ProductResponse>>(emptyList())
    val products: State<List<ProductResponse>> = _products

    // Флаг состояния загрузки
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    init {
        // Подписка на локальную базу данных
        viewModelScope.launch {
            repository.allProducts.collect { entities ->
                _products.value = entities.map {
                    ProductResponse(it.id, it.name, it.price, it.description, it.imageUrl)
                }
            }
        }
    }

    /**
     * Загружает актуальный список товаров из API и сохраняет в кэш.
     */
    fun fetchProducts() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.refreshProducts()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to fetch products", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Административная функция: Добавление нового товара.
     */
    fun addProduct(
        context: Context,
        name: String,
        price: String,
        description: String,
        imageUri: Uri,
        onSuccess: () -> Unit
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                val priceBody = price.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bytes = inputStream?.readBytes()
                if (bytes == null) throw Exception("Could not read image")
                
                val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", "product_new.jpg", requestFile)
                inputStream.close()

                repository.addProduct(nameBody, priceBody, descBody, filePart)
                onSuccess()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to add product", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Административная функция: Обновление существующего товара.
     */
    fun updateProduct(
        context: Context,
        id: Int,
        name: String,
        price: String,
        description: String,
        imageUri: Uri?,
        onSuccess: () -> Unit
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                val priceBody = price.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                
                var filePart: MultipartBody.Part? = null
                imageUri?.let { uri ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                        filePart = MultipartBody.Part.createFormData("file", "product_$id.jpg", requestFile)
                    }
                    inputStream?.close()
                }

                repository.updateProduct(id, nameBody, priceBody, descBody, filePart)
                onSuccess()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to update product", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Административная функция: Полное удаление товара.
     */
    fun deleteProduct(id: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.deleteProduct(id)
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to delete product", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Административная функция: Удаление только фотографии товара.
     */
    fun deleteProductPhoto(id: Int, onSuccess: () -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.deleteProductPhoto(id)
                onSuccess()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to delete product photo", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Фабрика для создания ShopViewModel.
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShopViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                val repository = ProductRepository(database.productDao(), application)
                @Suppress("UNCHECKED_CAST")
                return ShopViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
