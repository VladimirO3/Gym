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

/**
 * ViewModel для управления корзиной покупок.
 * Обеспечивает синхронизацию списка выбранных товаров с VPS сервером.
 */
class CartViewModel : ViewModel() {
    // Список товаров в корзине: Пара (Товар, Количество)
    private val _cartItems = mutableStateOf<List<Pair<ProductPlaceholder, Int>>>(emptyList())
    val cartItems: State<List<Pair<ProductPlaceholder, Int>>> = _cartItems

    private var syncJob: Job? = null
    private var currentToken: String? = null

    /**
     * Инициализация корзины. Загружает данные с сервера, если токен изменился или корзина пуста.
     */
    fun init(context: android.content.Context, token: String?) {
        val tokenChanged = currentToken != token
        currentToken = token
        
        // Если токен появился или изменился, принудительно загружаем данные с сервера
        if (token != null && token != "guest_token" && (tokenChanged || _cartItems.value.isEmpty())) {
            loadCartFromServer(context, token)
        }
    }

    /**
     * Получает актуальное состояние корзины из API.
     */
    private fun loadCartFromServer(context: android.content.Context, token: String) {
        viewModelScope.launch {
            try {
                // Используем API сервис с контекстом для автоматической подстановки токена
                val api = NewsApiService.create(context)
                val response = api.getCart()
                _cartItems.value = response.map {
                    Pair(
                        ProductPlaceholder(it.productId, it.name, it.price, it.description, it.imageUrl),
                        it.quantity
                    )
                }
                Log.i("CartViewModel", "Cart loaded from server for token: ${token.take(10)}... Size: ${response.size}")
            } catch (e: Exception) {
                Log.e("CartViewModel", "Failed to load cart from server", e)
            }
        }
    }

    /**
     * Синхронизирует текущее локальное состояние корзины с сервером.
     * Использует задержку (debounce) для предотвращения слишком частых запросов при быстром изменении количества.
     */
    private fun syncCartWithServer(context: android.content.Context) {
        val token = currentToken
        if (token == null || token == "guest_token") return
        
        // Отменяем предыдущую попытку синхронизации, если пользователь нажал кнопку еще раз
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1000) // Ждем 1 секунду "спокойствия" перед отправкой
            try {
                val api = NewsApiService.create(context)
                val request = _cartItems.value.map { (product, count) ->
                    CartItemRequest(product.id, count)
                }
                api.saveCart(request)
                Log.i("CartViewModel", "Cart synced with server. Items count: ${request.size}")
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
     * @param sync Если true, отправит пустой список на сервер для очистки облачной корзины.
     */
    fun clearCart(context: android.content.Context, sync: Boolean = true) {
        _cartItems.value = emptyList()
        if (sync) syncCartWithServer(context)
    }

    /**
     * Рассчитывает общую стоимость всех товаров, учитывая их количество.
     */
    fun getTotalPrice(): Int {
        return _cartItems.value.sumOf { (product, count) ->
            // Парсинг цены: убираем валюту и пробелы для получения числа
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
}
