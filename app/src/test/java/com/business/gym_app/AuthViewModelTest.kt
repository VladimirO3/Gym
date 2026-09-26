package com.business.gym_app

import android.app.Application
import com.business.gym_app.ui.viewmodel.AuthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Юнит-тесты для логики авторизации.
 * Проверяют переключения режимов и валидацию данных без участия UI и Firebase.
 */
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: AuthViewModel
    private val mockApplication = mock(Application::class.java)

    @Before
    fun setup() {
        // AuthViewModel при создании читает сохраненные данные входа и сессию из SharedPreferences.
        // Пустое хранилище = пользователь не авторизован, сетевых запросов не будет.
        val prefs = mutableMapOf<String, InMemorySharedPreferences>()
        `when`(mockApplication.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            prefs.getOrPut(invocation.getArgument(0)) { InMemorySharedPreferences() }
        }
        // Строки из ресурсов: у мок-Application нет resources, поэтому отдаём заглушку.
        val mockResources = mock(android.content.res.Resources::class.java)
        `when`(mockResources.getString(anyInt())).thenReturn("test_string")
        `when`(mockApplication.resources).thenReturn(mockResources)
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

    @Test
    fun testNoSavedSession_LoadsWithoutToken() {
        // Без сохраненного токена сессия считается загруженной, пользователь не авторизован
        assertTrue(viewModel.isSessionLoaded.value)
        assertNull(viewModel.jwtToken.value)
    }

    @Test
    fun testPinInput_OnlyDigitsMax4() {
        // PIN принимает только цифры и не длиннее 4 символов
        viewModel.onPinChange("12ab345")
        assertEquals("1234", viewModel.pinValue.value)
        viewModel.onPinConfirmChange(" 9x8y7z6 ")
        assertEquals("9876", viewModel.pinConfirm.value)
    }

    @Test
    fun testPinSetup_MismatchSetsError() {
        // Несовпадение PIN и подтверждения — ошибка, диалог не закрывается
        viewModel.requestPinSetup("user@test.com")
        assertTrue(viewModel.pendingPinSetup.value)
        viewModel.onPinChange("1234")
        viewModel.onPinConfirmChange("4321")
        var done = false
        viewModel.savePinCode("user@test.com") { done = true }
        assertFalse(done)
        assertTrue(viewModel.pendingPinSetup.value)
        assertTrue(!viewModel.error.value.isNullOrBlank())
    }

    @Test
    fun testRequestAndDismissPinSetup() {
        viewModel.requestPinSetup("user@test.com")
        assertTrue(viewModel.pendingPinSetup.value)
        assertEquals("user@test.com", viewModel.pinAccount.value)
        viewModel.dismissPinSetup()
        assertFalse(viewModel.pendingPinSetup.value)
    }

    @Test
    fun testDismissPinChange_BlockedWhenExpired() {
        // Просроченный PIN (7 дней) нельзя отменить — сначала задайте новый.
        viewModel.requestPinSetup("user@test.com", AuthViewModel.PinChangeReason.EXPIRED)
        var dismissed = false
        viewModel.dismissPinChange { dismissed = true }
        assertFalse(dismissed)
        assertTrue(viewModel.pendingPinSetup.value)
        assertTrue(!viewModel.error.value.isNullOrBlank())
    }

    @Test
    fun testDismissPinChange_AllowedWhenManual() {
        // Ручная смена / напоминание — можно отложить.
        viewModel.requestPinSetup("user@test.com", AuthViewModel.PinChangeReason.EXPIRING_SOON)
        var dismissed = false
        viewModel.dismissPinChange { dismissed = true }
        assertTrue(dismissed)
        assertFalse(viewModel.pendingPinSetup.value)
    }
}
