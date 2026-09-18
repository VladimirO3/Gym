package com.business.gym_app

import android.content.Context
import com.business.gym_app.data.local.dao.CartDao
import com.business.gym_app.data.local.dao.OrderDao
import com.business.gym_app.ui.screen.ProductPlaceholder
import com.business.gym_app.ui.viewmodel.CartViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Тесты для логики корзины с использованием Mockito.
 */
class CartViewModelTest {

    private lateinit var viewModel: CartViewModel
    private val mockContext = mock(Context::class.java)
    private val mockApplication = mock(android.app.Application::class.java)

    // Создаем заглушки для DAO
    private val mockDao = mock(CartDao::class.java)
    private val mockOrderDao = mock(OrderDao::class.java) // <-- ДОБАВЛЕНО: Создали мок для OrderDao

    private val testProduct = ProductPlaceholder(
        id = "1",
        name = "Test Product",
        price = "1 000 ₽",
        description = "Description",
        imageUrl = ""
    )

    @Before
    fun setup() {
        // Передаем все три параметра в конструктор (Application, CartDao, OrderDao)
        viewModel = CartViewModel(mockApplication, mockDao, mockOrderDao) // <-- ИСПРАВЛЕНО: Добавлен mockOrderDao
    }

    @Test
    fun testAddToCart_AddsItemToList() {
        viewModel.addToCart(mockContext, testProduct)
        assertEquals(1, viewModel.cartItems.value.size)
        assertEquals(1, viewModel.cartItems.value[0].second)
    }

    @Test
    fun testRemoveFromCart_RemovesItem() {
        viewModel.addToCart(mockContext, testProduct)
        viewModel.removeFromCart(mockContext, testProduct)
        assertEquals(0, viewModel.cartItems.value.size)
    }

    @Test
    fun testTotalPriceCalculation() {
        viewModel.addToCart(mockContext, testProduct)
        // Добавляем еще один такой же товар
        viewModel.addToCart(mockContext, testProduct)

        assertEquals(2000, viewModel.getTotalPrice())
    }

    @Test
    fun testFormatPrice() {
        val result = viewModel.formatPrice(2500)
        assertEquals("2 500 ₽", result.replace("\u00A0", " "))
    }
}
