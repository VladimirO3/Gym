package com.business.gym.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.api.ProductResponse
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ViewModel для управления товарами в магазине.
 * Обрабатывает получение списка товаров с сервера и административные функции (добавление/редактирование/удаление).
 */
class ShopViewModel(application: Application) : AndroidViewModel(application) {
    // Список товаров, полученных с сервера
    private val _products = mutableStateOf<List<ProductResponse>>(emptyList())
    val products: State<List<ProductResponse>> = _products

    // Флаг состояния загрузки
    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // Инициализация API сервиса через ленивую инициализацию
    private val api by lazy { NewsApiService.create(application) }

    /**
     * Загружает актуальный список товаров из API.
     */
    fun fetchProducts() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = api.getProducts()
                _products.value = response
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to fetch products", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Административная функция: Добавление нового товара.
     * @param imageUri URI фотографии из галереи телефона.
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
                // Подготовка текстовых полей для Multipart запроса
                val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                val priceBody = price.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                
                // Чтение файла изображения из URI
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bytes = inputStream?.readBytes()
                if (bytes == null) throw Exception("Could not read image")
                
                val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", "product_new.jpg", requestFile)
                inputStream.close()

                // Отправка данных на сервер
                api.addProduct(nameBody, priceBody, descBody, filePart)
                fetchProducts() // Обновляем список после добавления
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
     * @param imageUri Новое фото (необязательно).
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

                api.updateProduct(id, nameBody, priceBody, descBody, filePart)
                fetchProducts() // Обновляем список
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
                api.deleteProduct(id)
                fetchProducts()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to delete product", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Административная функция: Удаление только фотографии товара (замена на заглушку).
     */
    fun deleteProductPhoto(id: Int, onSuccess: () -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                api.deleteProductPhoto(id)
                fetchProducts()
                onSuccess()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to delete product photo", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
