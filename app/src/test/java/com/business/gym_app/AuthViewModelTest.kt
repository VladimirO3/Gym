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

    /** Хранилища, раздаваемые мок-контекстом: ключ — имя SharedPreferences. */
    private val prefs = mutableMapOf<String, InMemorySharedPreferences>()

    @Before
    fun setup() {
        // Строки из ресурсов: у мок-Application нет resources, поэтому отдаём заглушку.
        val mockResources = mock(android.content.res.Resources::class.java)
        `when`(mockResources.getString(anyInt())).thenReturn("test_string")
        `when`(mockApplication.resources).thenReturn(mockResources)
        viewModel = createViewModel()
    }

    /**
     * Создаёт ViewModel поверх prefs, наполненных в тесте.
     * Нужно для cold start: состояние сессии читается в конструкторе,
     * поэтому заполнять хранилище надо ДО создания ViewModel.
     */
    private fun createViewModel(): AuthViewModel {
        `when`(mockApplication.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            prefs.getOrPut(invocation.getArgument(0)) { InMemorySharedPreferences() }
        }
        return AuthViewModel(mockApplication)
    }

    /** Записывает значения в prefs так же, как это делает реальный код. */
    private fun seed(name: String, vararg pairs: Pair<String, Any?>) {
        val editor = prefs.getOrPut(name) { InMemorySharedPreferences() }.edit()
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                else -> editor.remove(key)
            }
        }
        editor.commit()
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
    fun testDismissPinChange_BlockedWhenRequired() {
        // Зарегистрирован без PIN — вход только по PIN/биометрии, диалог не закрыть.
        viewModel.requestPinSetup("user@test.com", AuthViewModel.PinChangeReason.REQUIRED)
        var dismissed = false
        viewModel.dismissPinChange { dismissed = true }
        assertFalse(dismissed)
        assertTrue(viewModel.pendingPinSetup.value)
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

    // --- Холодный старт: после выгрузки приложения вход подтверждается ---

    @Test
    fun testColdStart_NoSession_NoReauth() {
        // Сессии нет (первый запуск) — обычный экран входа, блокировки нет.
        assertFalse(viewModel.reauthRequired.value)
        assertTrue(viewModel.isSessionLoaded.value)
    }

    @Test
    fun testColdStart_SessionWithPin_RequiresReauth() {
        // Есть токен и PIN — в приложение не пускаем, ждём PIN/биометрию/пароль.
        seed(
            "auth_prefs",
            "user_session_token" to "jwt_token_123",
            "user_session_email" to "user@test.com"
        )
        seed(
            "pin_prefs",
            "pin_hash" to "hash",
            "pin_salt" to "salt",
            "pin_account" to "user@test.com"
        )
        val vm = createViewModel()
        assertTrue(vm.reauthRequired.value)
        assertTrue(vm.isSessionLoaded.value)
        assertNull(vm.currentUserEmail.value)
    }

    @Test
    fun testColdStart_SessionWithPasswordOnly_RequiresReauth() {
        // PIN нет, но есть сохранённый пароль — подтвердить вход тоже нужно.
        seed(
            "auth_prefs",
            "user_session_token" to "jwt_token_123",
            "user_session_email" to "user@test.com"
        )
        seed(
            "auth_credentials",
            "saved_email" to "user@test.com",
            "saved_password" to "secret"
        )
        val vm = createViewModel()
        assertTrue(vm.reauthRequired.value)
    }

    @Test
    fun testColdStart_GuestKeepsSession() {
        // Гость: подтверждать вход нечем, доступ не теряем.
        seed("auth_prefs", "user_session_token" to "guest_token")
        val vm = createViewModel()
        assertFalse(vm.reauthRequired.value)
    }

    @Test
    fun testColdStart_SessionWithoutPinAndPassword_Restores() {
        // Вход был по коду из письма: ни PIN, ни пароля — сессию восстанавливаем.
        seed(
            "auth_prefs",
            "user_session_token" to "jwt_token_123",
            "user_session_email" to "user@test.com"
        )
        val vm = createViewModel()
        assertFalse(vm.reauthRequired.value)
    }

    @Test
    fun testCompleteReauth_OpensApp() {
        seed(
            "auth_prefs",
            "user_session_token" to "jwt_token_123",
            "user_session_email" to "user@test.com"
        )
        seed("pin_prefs", "pin_hash" to "hash", "pin_salt" to "salt", "pin_account" to "user@test.com")
        val vm = createViewModel()
        assertTrue(vm.reauthRequired.value)
        vm.completeReauth()
        assertFalse(vm.reauthRequired.value)
    }

    @Test
    fun testCompleteReauth_KeepsSessionAfterPinLogin() {
        // После подтверждения входа PIN флаг снят — приложение открыто,
        // повторно логиниться на сервер не нужно.
        seed(
            "auth_prefs",
            "user_session_token" to "jwt_token_123",
            "user_session_email" to "user@test.com"
        )
        seed("pin_prefs", "pin_hash" to "hash", "pin_salt" to "salt", "pin_account" to "user@test.com")
        val vm = createViewModel()
        assertTrue(vm.reauthRequired.value)
        vm.completeReauth()
        assertFalse(vm.reauthRequired.value)
        // Сессионный токен не тронут — при следующей выгрузке потребуется новый вход.
        assertEquals(
            "jwt_token_123",
            prefs["auth_prefs"]!!.getString("user_session_token", null)
        )
    }
}
