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
        currentToken = token
        currentUserId = userId
        
        if (userId.isNullOrBlank()) {
            Log.w("CartViewModel", "Init: userId is blank, using 'guest' fallback for local DB")
        }

        val effectiveUserId = userId ?: "guest"

        viewModelScope.launch {
            _isInitializing.value = true
            try {
                // 1. Загрузка из локальной БД ( Room ) для мгновенного отклика
                val local = cartDao.getCartItems(effectiveUserId).first()
                if (local.isNotEmpty()) {
                    _cartItems.value = local.map { 
                        Pair(ProductPlaceholder(it.productId, it.name, it.price, it.description, it.imageUrl), it.quantity)
                    }
                }

                if (token != null && token != "guest_token" && userId != null) {
                    // 2. Мердж с гостевой корзиной при входе
                    val guestItems = cartDao.getCartItems("guest").first()
                    if (guestItems.isNotEmpty()) {
                        val currentList = _cartItems.value.toMutableList()
                        guestItems.forEach { g ->
                            val index = currentList.indexOfFirst { it.first.id == g.productId }
                            if (index == -1) {
                                currentList.add(Pair(ProductPlaceholder(g.productId, g.name, g.price, g.description, g.imageUrl), g.quantity))
                            } else {
                                // Если товар уже есть, можно либо оставить как есть, либо сложить количество
                                currentList[index] = currentList[index].copy(second = currentList[index].second + g.quantity)
                            }
                        }
                        _cartItems.value = currentList
                        cartDao.clearCart("guest")
                        
                        // Сохраняем объединенную корзину в БД пользователя
                        val entities = currentList.map { (p, count) ->
                            CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
                        }
                        cartDao.insertItems(entities)
                    }

                    // 3. Загрузка с сервера и мердж
                    loadCartFromServerInternal(context, userId)
                    
                    // 4. Загрузка истории
                    loadOrderHistoryFromServer(context, userId)
                    
                    orderDao.getUserOrders(userId).collect { entities ->
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
                }
            } catch (e: Exception) {
                Log.e("CartViewModel", "Init error", e)
            } finally {
                _isInitializing.value = false
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
            // Сначала обновляем UI
            _orderHistory.value = orders

            if (userId != null) {
                // Затем сохраняем в БД для офлайн доступа
                orders.forEach { order ->
                    try {
                        orderDao.insertOrder(com.business.gym.data.local.entity.OrderEntity(
                            order.id, userId, order.totalPrice, order.status, order.createdAt, gson.toJson(order.items)
                        ))
                    } catch (e: Exception) {
                        Log.e("CartViewModel", "Failed to cache order ${order.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CartViewModel", "Load orders failed", e)
            // При ошибке сети пытаемся загрузить из локальной БД
            if (userId != null) {
                try {
                    val localOrders = orderDao.getUserOrders(userId).first()
                    if (localOrders.isNotEmpty()) {
                        _orderHistory.value = localOrders.map { entity ->
                            OrderResponse(
                                id = entity.orderId,
                                totalPrice = entity.totalPrice,
                                status = entity.status,
                                createdAt = entity.createdAt,
                                items = gson.fromJson(entity.itemsJson, Array<com.business.gym.data.api.CartItemResponse>::class.java).toList()
                            )
                        }
                    }
                } catch (le: Exception) {
                    Log.e("CartViewModel", "Local order fetch failed", le)
                }
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
        
        // Для гостей сохраняем только в Room
        val userId = currentUserId ?: "guest"
        viewModelScope.launch {
            val entities = _cartItems.value.map { (p, count) ->
                CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
            }
            cartDao.clearCart(userId)
            cartDao.insertItems(entities)
        }
        
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

        // Для гостей сохраняем только в Room
        val userId = currentUserId ?: "guest"
        viewModelScope.launch {
            val entities = _cartItems.value.map { (p, count) ->
                CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
            }
            cartDao.clearCart(userId)
            cartDao.insertItems(entities)
        }

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

        val userId = currentUserId ?: "guest"
        
        // Всегда сохраняем локально, даже для гостей
        viewModelScope.launch {
            val entities = _cartItems.value.map { (p, count) ->
                CartItemEntity(userId, p.id, count, p.name, p.price, p.description, p.imageUrl)
            }
            cartDao.clearCart(userId)
            cartDao.insertItems(entities)
            Log.d("CartViewModel", "DEBUG: syncCartWithServer: Local DB updated for $userId")
        }

        val token = currentToken
        if (token == null || token == "guest_token" || currentUserId == null) {
            Log.d("CartViewModel", "syncCartWithServer: Skipping server sync for guest/no-token")
            return
        }

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(800) // Немного увеличим задержку для группировки быстрых нажатий
            try {
                val api = NewsApiService.create(context)
                val request = _cartItems.value.map { 
                    val rawId = it.first.id
                    val cleanIdString = rawId.removeSuffix(".0")
                    val idToSend: Any = cleanIdString.toIntOrNull() ?: cleanIdString
                    CartItemRequest(idToSend, it.second)
                }
                Log.d("CartViewModel", "DEBUG: syncCartWithServer: Sending ${request.size} items to server for user $userId")
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
