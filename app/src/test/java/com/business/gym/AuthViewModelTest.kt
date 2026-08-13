package com.business.gym

import android.app.Application
import com.business.gym.ui.viewmodel.AuthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Юнит-тесты для логики авторизации.
 * Проверяют переключения режимов и валидацию данных без участия UI и Firebase.
 */
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private val mockApplication = mock(Application::class.java)

    @Before
    fun setup() {
        // Используем мок Application, так как AuthViewModel является AndroidViewModel
        viewModel = AuthViewModel(mockApplication)
    }

    @Test
    fun testEmailStateUpdate() {
        // Проверка корректного обновления состояния email
        viewModel.onEmailChange("user@test.com")
        assertEquals("user@test.com", viewModel.email.value)
    }

    @Test
    fun testToggleIsLogin_ChangesState() {
        // Проверка переключения между Входом и Регистрацией
        val initialState = viewModel.isLogin.value
        viewModel.toggleIsLogin()
        assertEquals(!initialState, viewModel.isLogin.value)
    }

    @Test
    fun testAuthMode_EmailPhoneSwitch() {
        // Проверка смены режима (Email -> Телефон)
        viewModel.setAuthMode("phone")
        assertEquals("phone", viewModel.authMode.value)
    }

    @Test
    fun testIsStaticAdmin_ReturnsCorrectValue() {
        // Проверка логики определения администратора по email
        val adminEmail = AuthViewModel.ADMIN_EMAIL
        assertTrue(AuthViewModel.isStaticAdmin(adminEmail))
        assertTrue(AuthViewModel.isStaticAdmin(" $adminEmail ")) // Проверка обрезки пробелов
        assertTrue(AuthViewModel.isStaticAdmin(adminEmail.uppercase())) // Проверка регистра
        assertFalse(AuthViewModel.isStaticAdmin("regular@user.com"))
        assertFalse(AuthViewModel.isStaticAdmin(null))
    }
    
    private fun assertTrue(condition: Boolean) = assert(condition)
}
