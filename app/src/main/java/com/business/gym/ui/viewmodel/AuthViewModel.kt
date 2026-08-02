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

    private val _refreshToken = mutableStateOf<String?>(null)
    val refreshToken: State<String?> = _refreshToken

    private val localApiService get() = NewsApiService.create(getApplication())

    companion object {
        const val ADMIN_EMAIL = "verso0100@gmail.com"
        
        fun isStaticAdmin(email: String?): Boolean {
            return email?.trim()?.lowercase() == ADMIN_EMAIL.lowercase()
        }
    }

    fun isAdmin(): Boolean = isStaticAdmin(_currentUserEmail.value)

    // --- Поля для OTP ---
    private val _otpEmail = mutableStateOf("")
    val otpEmail: State<String> = _otpEmail

    private val _otpCode = mutableStateOf("")
    val otpCode: State<String> = _otpCode

    private val _authMode = mutableStateOf("email") // "email" или "phone" (теперь email OTP)
    val authMode: State<String> = _authMode

    fun onOtpEmailChange(newValue: String) { _otpEmail.value = newValue; _error.value = null }
    fun onOtpCodeChange(newValue: String) { _otpCode.value = newValue; _error.value = null }
    fun setAuthMode(mode: String) { _authMode.value = mode; _error.value = null }

    fun onEmailChange(newValue: String) { _email.value = newValue; _error.value = null }
    fun onPasswordChange(newValue: String) { _password.value = newValue; _error.value = null }
    fun onConfirmPasswordChange(newValue: String) { _confirmPassword.value = newValue; _error.value = null }
    
    fun toggleIsLogin() { _isLogin.value = !_isLogin.value; _error.value = null }

    fun loadSession(context: Context) {
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("user_session_email", null)
        val savedToken = sharedPref.getString("user_session_token", null)
        val savedRefreshToken = sharedPref.getString("user_session_refresh_token", null)
        
        if (savedEmail != null && savedToken != null) {
            _currentUserEmail.value = savedEmail
            _jwtToken.value = savedToken
            _refreshToken.value = savedRefreshToken
            _currentUid.value = savedEmail
        }
    }

    fun saveSession(context: Context, email: String, token: String, refreshToken: String? = null) {
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("user_session_email", email)
            putString("user_session_token", token)
            if (refreshToken != null) {
                putString("user_session_refresh_token", refreshToken)
            }
            apply()
        }
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
                Log.d("AuthViewModel", "Requesting OTP for: $emailValue")
                localApiService.login(emailValue, passwordValue)
                // ... (rest of logic)
            } catch (e: retrofit2.HttpException) {
                Log.e("AuthViewModel", "Login HTTP error: ${e.code()} ${e.message()}")
                _isLoading.value = false
                // ...
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login unexpected error", e)
                _isLoading.value = false
                _error.value = "Сервер недоступен"
            }
        }
    }

    fun requestOtp(activity: Activity? = null) {
        if (_otpEmail.value.isBlank()) {
            _error.value = "Введите email для получения кода"
            return
        }
        _isLoading.value = true
        val email = _otpEmail.value.trim().lowercase()
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Requesting OTP for email: $email")
                localApiService.requestOtp(email)
                Log.i("AuthViewModel", "OTP request successful for: $email")
                _isLoading.value = false
                _error.value = "Код отправлен на почту!"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to request OTP for $email", e)
                _isLoading.value = false
                _error.value = "Ошибка отправки: ${e.localizedMessage}"
            }
        }
    }

    fun verifyOtp(context: Context, onSuccess: () -> Unit) {
        if (_otpCode.value.isBlank()) {
            _error.value = "Введите код"
            return
        }
        _isLoading.value = true
        val email = _otpEmail.value.trim().lowercase()
        val code = _otpCode.value.trim()

        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Verifying OTP for $email with code $code")
                val response = localApiService.verifyOtp(email, code)
                Log.i("AuthViewModel", "OTP verification success for $email")
                
                _jwtToken.value = response.token
                _refreshToken.value = response.refreshToken
                
                _currentUserEmail.value = email
                _currentUid.value = email
                saveSession(getApplication(), email, response.token, response.refreshToken)
                _isLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "OTP verification failed for $email", e)
                _isLoading.value = false
                _error.value = "Неверный код или ошибка сервера"
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
        _refreshToken.value = null
        _currentUid.value = ""
        clearSession(getApplication())
    }
    
    // Заглушки и доп. методы
    val verificationId = mutableStateOf<String?>(null)
    val showSetPasswordDialog = mutableStateOf(false)
    val loginWithPasswordMode = mutableStateOf(false)
    fun dismissSetPasswordDialog() {}
    fun setPasswordForPhoneUser(p: String, s: () -> Unit) {}
    fun signInWithGoogle(t: String, s: (String) -> Unit) {}
    fun signInWithPhoneAndPassword(s: (String) -> Unit) {}
    fun retryLocalLogin(e: String) { signInWithEmail {} }
}
