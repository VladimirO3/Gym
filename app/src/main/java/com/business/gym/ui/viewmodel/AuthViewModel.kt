package com.business.gym.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.api.LocalUser
import kotlinx.coroutines.launch

/**
 * ViewModel для управления процессами авторизации через локальный сервер.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    // --- Поля ввода данных ---
    private val _email = mutableStateOf("")
    val email: State<String> = _email

    private val _password = mutableStateOf("")
    val password: State<String> = _password

    private val _confirmPassword = mutableStateOf("")
    val confirmPassword: State<String> = _confirmPassword

    private val _isLogin = mutableStateOf(true)
    val isLogin: State<Boolean> = _isLogin

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // --- Данные текущего пользователя ---
    private val _currentUserEmail = mutableStateOf<String?>(null)
    val currentUserEmail: State<String?> = _currentUserEmail

    private val _currentUid = mutableStateOf("")
    val currentUid: State<String> = _currentUid

    private val _jwtToken = mutableStateOf<String?>(null)
    val jwtToken: State<String?> = _jwtToken

    private val _pendingUsers = mutableStateOf(listOf<LocalUser>())
    val pendingUsers: State<List<LocalUser>> = _pendingUsers

    private val localApiService get() = NewsApiService.create()

    companion object {
        const val ADMIN_EMAIL = "verso0100@gmail.com"
        
        fun isStaticAdmin(email: String?): Boolean {
            return email?.trim()?.lowercase() == ADMIN_EMAIL.lowercase()
        }
    }

    fun isAdmin(): Boolean = isStaticAdmin(_currentUserEmail.value)

    fun onEmailChange(newValue: String) { _email.value = newValue; _error.value = null }
    fun onPasswordChange(newValue: String) { _password.value = newValue; _error.value = null }
    fun onConfirmPasswordChange(newValue: String) { _confirmPassword.value = newValue; _error.value = null }
    
    fun toggleIsLogin() { _isLogin.value = !_isLogin.value; _error.value = null }

    fun loadSession(context: Context) {
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("user_session_email", null)
        val savedToken = sharedPref.getString("user_session_token", null)
        
        if (savedEmail != null && savedToken != null) {
            _currentUserEmail.value = savedEmail
            _jwtToken.value = savedToken
            _currentUid.value = savedEmail
        }
    }

    fun saveSession(context: Context, email: String, token: String) {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("user_session_email", email)
            .putString("user_session_token", token)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun signInWithEmail(onSuccess: (String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }

        _isLoading.value = true
        val emailValue = _email.value.trim().lowercase()
        val passwordValue = _password.value

        viewModelScope.launch {
            try {
                val response = localApiService.login(emailValue, passwordValue)
                _jwtToken.value = response.token
                _currentUserEmail.value = emailValue
                _currentUid.value = emailValue
                saveSession(getApplication(), emailValue, response.token)
                _isLoading.value = false
                onSuccess(emailValue)
            } catch (e: retrofit2.HttpException) {
                _isLoading.value = false
                if (e.code() == 403) {
                    _error.value = "Ваш аккаунт ожидает подтверждения администратором"
                } else {
                    _error.value = "Ошибка входа: Неверные данные"
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = "Сервер недоступен"
            }
        }
    }

    fun signUpWithEmail(onSuccess: (String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank() || _confirmPassword.value.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }
        if (_password.value != _confirmPassword.value) {
            _error.value = "Пароли не совпадают"
            return
        }

        _isLoading.value = true
        val emailValue = _email.value.trim().lowercase()
        val passwordValue = _password.value

        viewModelScope.launch {
            try {
                localApiService.register(emailValue, passwordValue, emailValue.substringBefore("@"))
                _isLoading.value = false
                _isLogin.value = true // Переключаемся на вход
                _error.value = "Заявка отправлена! Ожидайте подтверждения администратором."
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = "Ошибка регистрации: ${e.localizedMessage}"
            }
        }
    }

    fun fetchPendingUsers() {
        val token = _jwtToken.value ?: return
        viewModelScope.launch {
            try {
                _pendingUsers.value = localApiService.getPendingUsers("Bearer $token")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to fetch pending users", e)
            }
        }
    }

    fun approveUser(userUid: String) {
        val token = _jwtToken.value ?: return
        viewModelScope.launch {
            try {
                localApiService.approveUser("Bearer $token", userUid)
                fetchPendingUsers() // Обновляем список
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to approve user", e)
            }
        }
    }

    fun signOut() {
        _currentUserEmail.value = null
        _jwtToken.value = null
        _currentUid.value = ""
        clearSession(getApplication())
    }
    
    // Заглушки для совместимости
    val authMode = mutableStateOf("email")
    val phoneNumber = mutableStateOf("")
    val otpCode = mutableStateOf("")
    val verificationId = mutableStateOf<String?>(null)
    val showSetPasswordDialog = mutableStateOf(false)
    val loginWithPasswordMode = mutableStateOf(false)
    fun setAuthMode(mode: String) {}
    fun dismissSetPasswordDialog() {}
    fun setPasswordForPhoneUser(p: String, s: () -> Unit) {}
    fun startPhoneVerification(a: Activity) {}
    fun verifyOtp(c: Context, s: () -> Unit) {}
    fun signInWithGoogle(t: String, s: (String) -> Unit) {}
    fun signInWithPhoneAndPassword(s: (String) -> Unit) {}
    fun retryLocalLogin(e: String) { signInWithEmail {} }
}
