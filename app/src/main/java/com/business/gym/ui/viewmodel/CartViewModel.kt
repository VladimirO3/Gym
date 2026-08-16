package com.business.gym.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.CartItemRequest
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.dao.CartDao
import com.business.gym.data.local.entity.CartItemEntity
import com.business.gym.ui.screen.ProductPlaceholder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel для управления корзиной покупок.
 * Обеспечивает синхронизацию списка выбранных товаров с VPS сервером и локальной БД Room.
 */
class CartViewModel(
    application: Application,
    private val cartDao: CartDao
) : AndroidViewModel(application) {
    
    // Список товаров в корзине: Пара (Товар, Количество)
    private val _cartItems = mutableStateOf<List<Pair<ProductPlaceholder, Int>>>(emptyList())
    val cartItems: State<List<Pair<ProductPlaceholder, Int>>> = _cartItems

    private var syncJob: Job? = null
    private var currentToken: String? = null
    private var currentUserId: String? = null

    /**
     * Инициализация корзины. Загружает данные с сервера и локальной БД.
     */
    fun init(context: android.content.Context, token: String?, userId: String? = null) {
        val tokenChanged = currentToken != token
        currentToken = token
        currentUserId = userId
        
        if (token != null && token != "guest_token") {
            // 1. Загружаем из локальной БД для быстрого отображения
            userId?.let { uid ->
                viewModelScope.launch {
                    val localItems = cartDao.getCartItems(uid).first()
                    if (_cartItems.value.isEmpty() && localItems.isNotEmpty()) {
                        _cartItems.value = localItems.map {
                            Pair(
                                ProductPlaceholder(it.productId, it.name, it.price, it.description, it.imageUrl),
                                it.quantity
                            )
                        }
                        Log.d("CartViewModel", "Cart loaded from local Room cache for user: $uid")
                    }
                    
                    // 2. Затем актуализируем с сервера
                    loadCartFromServer(context, token, uid)
                }
            } ?: run {
                // Если UID нет, просто пробуем сервер (напр. переходный период)
                loadCartFromServer(context, token, null)
            }
        }
    }

    /**
     * Получает актуальное состояние корзины из API и сохраняет в Room.
     */
    private fun loadCartFromServer(context: android.content.Context, token: String, userId: String?) {
        viewModelScope.launch {
            try {
                val api = NewsApiService.create(context)
                val response = api.getCart()
                val newItems = response.map {
                    Pair(
                        ProductPlaceholder(it.productId, it.name, it.price, it.description, it.imageUrl),
                        it.quantity
                    )
                }
                _cartItems.value = newItems
                
                // Сохраняем в локальную БД
                if (userId != null) {
                    val entities = newItems.map { (p, count) ->
                        CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
                    }
                    cartDao.clearCart(userId)
                    cartDao.insertItems(entities)
                }
                
                Log.i("CartViewModel", "Cart synced from VPS. Size: ${response.size}")
            } catch (e: Exception) {
                Log.e("CartViewModel", "Failed to load cart from server", e)
            }
        }
    }

    /**
     * Синхронизирует текущее локальное состояние корзины с сервером и Room.
     */
    private fun syncCartWithServer(context: android.content.Context) {
        val sharedPref = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
        val savedToken = sharedPref.getString("user_session_token", null)
        val savedUid = sharedPref.getString("user_session_uid", null) ?: sharedPref.getString("user_session_email", null)

        val token = currentToken ?: savedToken
        val userId = currentUserId ?: savedUid
        
        if (token == null || token == "guest_token") return
        
        // Сначала сохраняем в Room (мгновенно)
        if (userId != null) {
            viewModelScope.launch {
                val entities = _cartItems.value.map { (p, count) ->
                    CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
                }
                cartDao.clearCart(userId)
                cartDao.insertItems(entities)
            }
        }

        // Затем на сервер с минимальной задержкой
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(300)
            try {
                val api = NewsApiService.create(context)
                val request = _cartItems.value.map { (product, count) ->
                    CartItemRequest(product.id, count)
                }
                api.saveCart(request)
                Log.i("CartViewModel", "Cart synced with VPS success")
            } catch (e: Exception) {
                Log.e("CartViewModel", "Failed to sync cart with server", e)
            }
        }
    }

    /**
     * Добавляет товар в корзину или увеличивает его количество.
     */
    fun addToCart(context: android.content.Context, product: ProductPlaceholder) {
        val currentItems = _cartItems.value.toMutableList()
        val existingItemIndex = currentItems.indexOfFirst { it.first.id == product.id }
        
        if (existingItemIndex != -1) {
            val (p, count) = currentItems[existingItemIndex]
            currentItems[existingItemIndex] = Pair(p, count + 1)
        } else {
            currentItems.add(Pair(product, 1))
        }
        _cartItems.value = currentItems
        syncCartWithServer(context)
    }

    /**
     * Уменьшает количество товара или удаляет его, если количество стало равным 0.
     */
    fun removeFromCart(context: android.content.Context, product: ProductPlaceholder) {
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
        syncCartWithServer(context)
    }

    /**
     * Полная очистка корзины.
     */
    fun clearCart(context: android.content.Context, sync: Boolean = true) {
        _cartItems.value = emptyList()
        if (sync) {
            currentUserId?.let { uid ->
                viewModelScope.launch { cartDao.clearCart(uid) }
            }
            syncCartWithServer(context)
        }
    }

    /**
     * Рассчитывает общую стоимость всех товаров, учитывая их количество.
     */
    fun getTotalPrice(): Int {
        return _cartItems.value.sumOf { (product, count) ->
            val priceCleaned = product.price.replace(" ", "").replace("\u00A0", "")
            val match = Regex("(\\d+)").find(priceCleaned)
            val priceInt = match?.value?.toIntOrNull() ?: 0
            priceInt * count
        }
    }

    /**
     * Форматирует число в строку вида "1 500 ₽"
     */
    fun formatPrice(price: Int): String {
        return String.format(java.util.Locale("ru", "RU"), "%, d", price).replace(",", " ").trim() + " ₽"
    }

    /**
     * Фабрика для создания CartViewModel.
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CartViewModel::class.java)) {
                val database = GymDatabase.getDatabase(application)
                @Suppress("UNCHECKED_CAST")
                return CartViewModel(application, database.cartDao()) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
