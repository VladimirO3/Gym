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

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val _products = mutableStateOf<List<ProductResponse>>(emptyList())
    val products: State<List<ProductResponse>> = _products

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val api by lazy { NewsApiService.create(application) }

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

                api.addProduct(nameBody, priceBody, descBody, filePart)
                fetchProducts()
                onSuccess()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to add product", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

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
                fetchProducts()
                onSuccess()
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Failed to update product", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

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
