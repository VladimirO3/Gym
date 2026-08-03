package com.business.gym.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.CartItemRequest
import com.business.gym.data.api.NewsApiService
import com.business.gym.ui.screen.ProductPlaceholder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CartViewModel : ViewModel() {
    private val _cartItems = mutableStateOf<List<Pair<ProductPlaceholder, Int>>>(emptyList())
    val cartItems: State<List<Pair<ProductPlaceholder, Int>>> = _cartItems

    private var syncJob: Job? = null
    private var currentToken: String? = null

    fun init(token: String?) {
        currentToken = token
        if (token != null && _cartItems.value.isEmpty()) {
            loadCartFromServer(token)
        }
    }

    private fun loadCartFromServer(token: String) {
        viewModelScope.launch {
            try {
                val api = NewsApiService.create()
                val response = api.getCart("Bearer $token")
                _cartItems.value = response.map {
                    Pair(
                        ProductPlaceholder(it.productId, it.name, it.price, it.description),
                        it.quantity
                    )
                }
                Log.i("CartViewModel", "Cart loaded from server: ${response.size} items")
            } catch (e: Exception) {
                Log.e("CartViewModel", "Failed to load cart", e)
            }
        }
    }

    private fun syncCartWithServer() {
        val token = currentToken ?: return
        
        // Отменяем предыдущую попытку синхронизации (debounce)
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1000) // Ждем 1 секунду перед отправкой, вдруг пользователь нажмет еще раз
            try {
                val api = NewsApiService.create()
                val request = _cartItems.value.map { (product, count) ->
                    CartItemRequest(product.id, count)
                }
                api.saveCart("Bearer $token", request)
                Log.i("CartViewModel", "Cart synced with server")
            } catch (e: Exception) {
                Log.e("CartViewModel", "Failed to sync cart", e)
            }
        }
    }

    fun addToCart(product: ProductPlaceholder) {
        val currentItems = _cartItems.value.toMutableList()
        val existingItemIndex = currentItems.indexOfFirst { it.first.id == product.id }
        
        if (existingItemIndex != -1) {
            val (p, count) = currentItems[existingItemIndex]
            currentItems[existingItemIndex] = Pair(p, count + 1)
        } else {
            currentItems.add(Pair(product, 1))
        }
        _cartItems.value = currentItems
        syncCartWithServer()
    }

    fun removeFromCart(product: ProductPlaceholder) {
        val currentItems = _cartItems.value.toMutableList()
        val existingItemIndex = currentItems.indexOfFirst { it.first.id == product.id }
        
        if (existingItemIndex != -1) {
            val (p, count) = currentItems[existingItemIndex]
            if (count > 1) {
                currentItems[existingItemIndex] = Pair(p, count - 1)
            } else {
                currentItems.removeAt(existingItemIndex)
            }
        }
        _cartItems.value = currentItems
        syncCartWithServer()
    }

    fun clearCart(sync: Boolean = true) {
        _cartItems.value = emptyList()
        if (sync) syncCartWithServer()
    }

    fun getTotalPrice(): Int {
        return _cartItems.value.sumOf { (product, count) ->
            val priceInt = product.price.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            priceInt * count
        }
    }
}
