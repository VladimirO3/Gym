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
import com.business.gym.data.api.OrderResponse
import com.business.gym.data.local.GymDatabase
import com.business.gym.data.local.dao.CartDao
import com.business.gym.data.local.dao.OrderDao
import com.business.gym.data.local.entity.CartItemEntity
import com.business.gym.ui.screen.ProductPlaceholder
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel для управления корзиной покупок.
 */
class CartViewModel(
    application: Application,
    private val cartDao: CartDao,
    private val orderDao: OrderDao
) : AndroidViewModel(application) {
    
    private val _cartItems = mutableStateOf<List<Pair<ProductPlaceholder, Int>>>(emptyList())
    val cartItems: State<List<Pair<ProductPlaceholder, Int>>> = _cartItems

    private val _orderHistory = mutableStateOf<List<OrderResponse>>(emptyList())
    val orderHistory: State<List<OrderResponse>> = _orderHistory

    private val _isInitializing = mutableStateOf(false)
    val isInitializing: State<Boolean> = _isInitializing

    private var syncJob: Job? = null
    private var currentToken: String? = null
    private var currentUserId: String? = null
    private val gson = Gson()

    fun init(context: android.content.Context, token: String?, userId: String? = null) {
        if (userId.isNullOrBlank() || token.isNullOrBlank()) {
            Log.w("CartViewModel", "Init skipped: blank userId or token")
            return
        }
        
        currentToken = token
        currentUserId = userId
        
        if (token != "guest_token") {
            userId.let { uid ->
                _isInitializing.value = true
                viewModelScope.launch {
                    try {
                        // 1. Мердж с гостевой корзиной
                        val guestItems = cartDao.getCartItems("guest").first()
                        if (guestItems.isNotEmpty()) {
                            val currentList = _cartItems.value.toMutableList()
                            guestItems.forEach { g ->
                                val existing = currentList.find { it.first.id == g.productId }
                                if (existing == null) {
                                    currentList.add(Pair(ProductPlaceholder(g.productId, g.name, g.price, g.description, g.imageUrl), g.quantity))
                                }
                            }
                            _cartItems.value = currentList
                            cartDao.clearCart("guest")
                        }

                        // 2. Загрузка с сервера и мердж
                        loadCartFromServerInternal(context, uid)
                        
                        // 3. Загрузка истории
                        orderDao.getUserOrders(uid).collect { entities ->
                            _orderHistory.value = entities.map { entity ->
                                OrderResponse(
                                    id = entity.orderId,
                                    totalPrice = entity.totalPrice,
                                    status = entity.status,
                                    createdAt = entity.createdAt,
                                    items = gson.fromJson(entity.itemsJson, Array<com.business.gym.data.api.CartItemResponse>::class.java).toList()
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CartViewModel", "Init error", e)
                    } finally {
                        _isInitializing.value = false
                        syncCartWithServer(context)
                    }
                }
                
                viewModelScope.launch {
                    loadOrderHistoryFromServer(context, uid)
                }
            }
        }
    }

    private suspend fun loadCartFromServerInternal(context: android.content.Context, userId: String?) {
        Log.d("CartViewModel", "DEBUG: Starting loadCartFromServerInternal for userId: $userId")
        try {
            val api = NewsApiService.create(context)
            val response = api.getCart()
            
            Log.d("CartViewModel", "DEBUG: Received cart from server. Item count: ${response.size}")
            
            if (response.isNotEmpty()) {
                val serverItems = response.map {
                    // Очищаем ID от возможных .0 при десериализации Double из Any
                    val rawId = it.productId.toString()
                    val cleanId = rawId.removeSuffix(".0")
                    Log.d("CartViewModel", "DEBUG: Server item: ID=$cleanId (raw=${it.productId}), Name=${it.name}, Qty=${it.quantity}")
                    Pair(ProductPlaceholder(cleanId, it.name, it.price, it.description, it.imageUrl), it.quantity)
                }
                
                val mergedList = _cartItems.value.toMutableList()
                serverItems.forEach { s ->
                    val index = mergedList.indexOfFirst { it.first.id == s.first.id }
                    if (index != -1) mergedList[index] = s else mergedList.add(s)
                }
                _cartItems.value = mergedList
                
                if (userId != null) {
                    val entities = mergedList.map { (p, count) ->
                        CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
                    }
                    cartDao.clearCart(userId)
                    cartDao.insertItems(entities)
                    Log.d("CartViewModel", "DEBUG: Saved ${entities.size} items to Room for user $userId")
                }
            } else {
                Log.d("CartViewModel", "DEBUG: Server cart is EMPTY.")
                if (_cartItems.value.isEmpty() && userId != null) {
                    val local = cartDao.getCartItems(userId).first()
                    Log.d("CartViewModel", "DEBUG: Local DB items found: ${local.size}")
                    if (local.isNotEmpty()) {
                        _cartItems.value = local.map { 
                            Pair(ProductPlaceholder(it.productId, it.name, it.price, it.description, it.imageUrl), it.quantity)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CartViewModel", "DEBUG: CRITICAL: Load cart failed", e)
            if (e is retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("CartViewModel", "DEBUG: Load cart HTTP Error: ${e.code()}, Body: $errorBody")
            }
        }
    }

    private suspend fun loadOrderHistoryFromServer(context: android.content.Context, userId: String?) {
        try {
            val api = NewsApiService.create(context)
            val orders = api.getOrders()
            _orderHistory.value = orders

            if (userId != null) {
                orders.forEach { order ->
                    orderDao.insertOrder(com.business.gym.data.local.entity.OrderEntity(
                        order.id, userId, order.totalPrice, order.status, order.createdAt, gson.toJson(order.items)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("CartViewModel", "Load orders failed", e)
            if (e is retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("CartViewModel", "Load orders HTTP Error: ${e.code()}, Body: $errorBody")
            }
        }
    }

    fun checkout(context: android.content.Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val api = NewsApiService.create(context)
                val response = api.checkout()
                clearCart(context, sync = true)
                currentUserId?.let { loadOrderHistoryFromServer(context, it) }
                onSuccess()
            } catch (e: Exception) {
                Log.e("CartViewModel", "Checkout failed", e)
                if (e is retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e("CartViewModel", "Checkout HTTP Error: ${e.code()}, Body: $errorBody")
                }
                android.widget.Toast.makeText(context, "Ошибка оформления", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addToCart(context: android.content.Context, product: ProductPlaceholder) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.first.id == product.id }
        if (index != -1) {
            currentItems[index] = currentItems[index].copy(second = currentItems[index].second + 1)
        } else {
            currentItems.add(Pair(product, 1))
        }
        _cartItems.value = currentItems
        syncCartWithServer(context)
    }

    fun removeFromCart(context: android.content.Context, product: ProductPlaceholder) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.first.id == product.id }
        if (index != -1) {
            if (currentItems[index].second > 1) {
                currentItems[index] = currentItems[index].copy(second = currentItems[index].second - 1)
            } else {
                currentItems.removeAt(index)
            }
        }
        _cartItems.value = currentItems
        syncCartWithServer(context)
    }

    fun clearCart(context: android.content.Context, sync: Boolean = true) {
        _cartItems.value = emptyList()
        if (sync) {
            currentUserId?.let { uid -> viewModelScope.launch { cartDao.clearCart(uid) } }
            syncCartWithServer(context)
        }
    }

    private fun syncCartWithServer(context: android.content.Context) {
        if (_isInitializing.value) {
            Log.d("CartViewModel", "DEBUG: syncCartWithServer skipped (isInitializing=true)")
            return
        }
        val token = currentToken ?: return
        if (token == "guest_token") return

        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Log.w("CartViewModel", "syncCartWithServer skipped: blank currentUserId")
            return
        }

        viewModelScope.launch {
            val entities = _cartItems.value.map { (p, count) ->
                CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
            }
            cartDao.clearCart(userId)
            cartDao.insertItems(entities)
            Log.d("CartViewModel", "DEBUG: syncCartWithServer: Local DB updated for $userId")
        }

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(500)
            try {
                val api = NewsApiService.create(context)
                // Очищаем ID перед отправкой на сервер. Если ID числовой - отправляем как число.
                val request = _cartItems.value.map { 
                    val rawId = it.first.id
                    val cleanIdString = rawId.removeSuffix(".0")
                    val idToSend: Any = cleanIdString.toIntOrNull() ?: cleanIdString
                    CartItemRequest(idToSend, it.second)
                }
                Log.d("CartViewModel", "DEBUG: syncCartWithServer: Sending ${request.size} items to server")
                api.saveCart(request)
            } catch (e: Exception) {
                Log.e("CartViewModel", "DEBUG: Sync to server failed", e)
            }
        }
    }

    fun getTotalPrice(): Int {
        return _cartItems.value.sumOf { (product, count) ->
            val match = Regex("(\\d+)").find(product.price.replace("[^\\d]".toRegex(), ""))
            (match?.value?.toIntOrNull() ?: 0) * count
        }
    }

    fun formatPrice(price: Int): String {
        return String.format(java.util.Locale("ru", "RU"), "%, d", price).replace(",", " ").trim() + " ₽"
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = GymDatabase.getDatabase(application)
            return CartViewModel(application, db.cartDao(), db.orderDao()) as T
        }
    }
}
