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
import com.business.gym.data.api.LoginRequest
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

    private val _regPhone = mutableStateOf("")
    val regPhone: State<String> = _regPhone

    private val _privacyAgreed = mutableStateOf(false)
    val privacyAgreed: State<Boolean> = _privacyAgreed

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

    private val _isGuest = mutableStateOf(false)
    val isGuest: State<Boolean> = _isGuest

    private val localApiService get() = NewsApiService.create(getApplication())

    companion object {
        const val ADMIN_EMAIL = "verso0100@gmail.com"
        const val GUEST_EMAIL = "guest@gym.app"
        
        fun isStaticAdmin(email: String?): Boolean {
            return email?.trim()?.lowercase() == ADMIN_EMAIL.lowercase()
        }
    }

    fun isAdmin(): Boolean = isStaticAdmin(_currentUserEmail.value)

    fun loginAsGuest(onSuccess: () -> Unit) {
        _isGuest.value = true
        _currentUserEmail.value = GUEST_EMAIL
        _currentUid.value = "guest"
        _jwtToken.value = "guest_token" // Заглушка токена
        onSuccess()
    }

    // --- Поля для OTP и пароля ---
    private val _otpEmail = mutableStateOf("")
    val otpEmail: State<String> = _otpEmail

    private val _otpPhone = mutableStateOf("")
    val otpPhone: State<String> = _otpPhone

    private val _otpCode = mutableStateOf("")
    val otpCode: State<String> = _otpCode

    private val _authMode = mutableStateOf("email") // "email" или "phone"
    val authMode: State<String> = _authMode

    private val _isPasswordMode = mutableStateOf(false)
    val isPasswordMode: State<Boolean> = _isPasswordMode

    fun togglePasswordMode() { 
        _isPasswordMode.value = !_isPasswordMode.value
        _error.value = null 
    }

    fun onOtpEmailChange(newValue: String) { 
        _otpEmail.value = newValue
        _email.value = newValue // Синхронизируем с полем для обычного входа
        _error.value = null 
        
        // Автоматически предлагаем ввод пароля для админа
        if (isStaticAdmin(newValue)) {
            _isPasswordMode.value = true
        }
    }

    private fun saveCredentials(email: String, pass: String) {
        val sharedPref = getApplication<Application>().getSharedPreferences("auth_credentials", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("saved_email", email)
            putString("saved_password", pass)
            apply()
        }
    }

    private fun loadCredentials() {
        val sharedPref = getApplication<Application>().getSharedPreferences("auth_credentials", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("saved_email", "") ?: ""
        val savedPassword = sharedPref.getString("saved_password", "") ?: ""
        
        if (savedEmail.isNotBlank()) {
            _otpEmail.value = savedEmail
            _email.value = savedEmail
            _password.value = savedPassword
            if (isStaticAdmin(savedEmail) || savedPassword.isNotBlank()) {
                _isPasswordMode.value = true
            }
        }
    }

    init {
        loadCredentials()
        loadSession(getApplication())
    }
    fun onOtpPhoneChange(newValue: String) { _otpPhone.value = newValue; _error.value = null }
    fun onOtpCodeChange(newValue: String) { _otpCode.value = newValue; _error.value = null }
    fun setAuthMode(mode: String) { _authMode.value = mode; _error.value = null }

    fun onEmailChange(newValue: String) { _email.value = newValue; _error.value = null }
    fun onPasswordChange(newValue: String) { _password.value = newValue; _error.value = null }
    fun onConfirmPasswordChange(newValue: String) { _confirmPassword.value = newValue; _error.value = null }
    fun onRegPhoneChange(newValue: String) { _regPhone.value = newValue; _error.value = null }
    fun onPrivacyAgreementChange(agreed: Boolean) { _privacyAgreed.value = agreed; _error.value = null }
    
    fun toggleIsLogin() { 
        _isLogin.value = !_isLogin.value
        _error.value = null 
        // Не очищаем email и телефон для удобства
        _password.value = ""
        _confirmPassword.value = ""
        _privacyAgreed.value = false
    }

    fun loadSession(context: Context) {
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("user_session_email", null)
        val savedPhone = sharedPref.getString("user_session_phone", null)
        val savedToken = sharedPref.getString("user_session_token", null)
        val savedRefreshToken = sharedPref.getString("user_session_refresh_token", null)
        val savedUid = sharedPref.getString("user_session_uid", null)
        
        if (savedToken != null) {
            _currentUserEmail.value = savedEmail
            _jwtToken.value = savedToken
            _refreshToken.value = savedRefreshToken
            _currentUid.value = savedUid ?: savedEmail ?: savedPhone ?: "user"
            _isGuest.value = savedToken == "guest_token"
            
            // Если UID отсутствует или совпадает с email, пытаемся актуализировать его с сервера
            if (savedUid == null && savedToken != "guest_token") {
                fetchAndSaveProfile { /* ignore success/fail here */ }
            }
        }
    }

    fun saveSession(context: Context, email: String?, phone: String?, token: String, refreshToken: String? = null, uid: String? = null) {
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("user_session_email", email)
            putString("user_session_phone", phone)
            putString("user_session_token", token)
            if (refreshToken != null) {
                putString("user_session_refresh_token", refreshToken)
            }
            if (uid != null) {
                putString("user_session_uid", uid)
            }
            commit() // Используем commit для немедленной записи токенов
        }
        Log.d("AuthViewModel", "Session saved for: $email. UID: $uid")
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun fetchAndSaveProfile(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = localApiService.getProfile()
                _currentUid.value = profile.uid
                _currentUserEmail.value = profile.email
                saveSession(getApplication(), profile.email, null, _jwtToken.value!!, _refreshToken.value, profile.uid)
                onSuccess(profile.email)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to fetch profile after login", e)
                // Если не удалось получить профиль, используем email как временный UID
                onSuccess(_currentUserEmail.value ?: "")
            }
        }
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
                Log.d("AuthViewModel", "Attempting login to: ${NewsApiService.getBaseUrl()}login with email: $emailValue")
                val response = localApiService.login(emailValue, passwordValue)
                
                Log.d("AuthViewModel", "Login successful, token received")
                _jwtToken.value = response.token
                _refreshToken.value = response.refreshToken
                
                // Сначала сохраняем токен, чтобы последующий вызов getProfile прошел успешно
                saveSession(getApplication(), emailValue, null, response.token, response.refreshToken)
                saveCredentials(emailValue, passwordValue) 

                // Получаем реальный UID и данные профиля с сервера
                fetchAndSaveProfile { email ->
                    _isLoading.value = false
                    onSuccess(email)
                }
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("AuthViewModel", "Login HTTP error: ${e.code()} ${e.message()} Body: $errorBody")
                _isLoading.value = false
                _error.value = "Ошибка входа: ${e.code()}. ${errorBody ?: ""}"
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.ConnectException -> "Сервер недоступен (${NewsApiService.getBaseUrl()}). Проверьте IP-адрес (иконка шестеренки)."
                    is java.net.SocketTimeoutException -> "Время ожидания истекло. Проверьте сеть."
                    is java.net.UnknownHostException -> "Хост не найден. Проверьте правильность IP."
                    else -> "Ошибка сервера: ${e.localizedMessage}"
                }
                Log.e("AuthViewModel", "Login error: $errorMessage", e)
                _isLoading.value = false
                _error.value = errorMessage
            }
        }
    }

    fun requestOtp(activity: Activity? = null) {
        val isEmail = _authMode.value == "email"
        val target = if (isEmail) _otpEmail.value.trim().lowercase() else _otpPhone.value.trim()
        
        if (target.isBlank()) {
            _error.value = "Введите ${if (isEmail) "email" else "номер телефона"}"
            return
        }
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Requesting OTP for: $target")
                if (isEmail) {
                    localApiService.requestOtp(email = target)
                } else {
                    localApiService.requestOtp(phone = target)
                }
                Log.i("AuthViewModel", "OTP request successful for: $target")
                _isLoading.value = false
                _error.value = "Код отправлен!"
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.ConnectException -> "Сервер недоступен (${NewsApiService.getBaseUrl()}). Проверьте IP-адрес (иконка шестеренки)."
                    is java.net.SocketTimeoutException -> "Время ожидания истекло. Проверьте сеть."
                    is java.net.UnknownHostException -> "Хост не найден. Проверьте правильность IP."
                    else -> "Ошибка отправки: ${e.localizedMessage}"
                }
                Log.e("AuthViewModel", "Failed to request OTP for $target", e)
                _isLoading.value = false
                _error.value = errorMessage
            }
        }
    }

    fun verifyOtp(context: Context, onSuccess: (String) -> Unit) {
        if (_otpCode.value.isBlank()) {
            _error.value = "Введите код"
            return
        }
        _isLoading.value = true
        val isEmail = _authMode.value == "email"
        val email = if (isEmail) _otpEmail.value.trim().lowercase() else null
        val phone = if (!isEmail) _otpPhone.value.trim() else null
        val code = _otpCode.value.trim()

        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Verifying OTP for $email / $phone with code $code")
                val response = localApiService.verifyOtp(email = email, phone = phone, otp = code)
                Log.i("AuthViewModel", "OTP verification success")
                
                _jwtToken.value = response.token
                _refreshToken.value = response.refreshToken
                
                saveSession(getApplication(), email, phone, response.token, response.refreshToken)
                
                if (email != null) {
                    saveCredentials(email, "")
                }

                fetchAndSaveProfile { userEmail ->
                    _isLoading.value = false
                    onSuccess(userEmail)
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is java.net.ConnectException -> "Сервер недоступен (${NewsApiService.getBaseUrl()}). Проверьте IP-адрес (иконка шестеренки)."
                    is java.net.SocketTimeoutException -> "Время ожидания истекло. Проверьте сеть."
                    is java.net.UnknownHostException -> "Хост не найден. Проверьте правильность IP."
                    else -> "Неверный код или ошибка сервера"
                }
                Log.e("AuthViewModel", "OTP verification failed", e)
                _isLoading.value = false
                _error.value = errorMessage
            }
        }
    }

    fun signUpWithEmail(onSuccess: (String) -> Unit) {
        // Используем otpEmail, так как именно оно привязано к текстовому полю на экране
        val emailValue = _otpEmail.value.trim().lowercase()
        val phoneValue = _regPhone.value.trim()
        val passwordValue = _password.value
        val confirmValue = _confirmPassword.value

        if (emailValue.isBlank() || phoneValue.isBlank() || passwordValue.isBlank() || confirmValue.isBlank()) {
            _error.value = "Заполните все обязательные поля"
            return
        }
        if (passwordValue != confirmValue) {
            _error.value = "Пароли не совпадают"
            return
        }
        if (!_privacyAgreed.value) {
            _error.value = "Необходимо согласиться с политикой конфиденциальности"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                localApiService.register(
                    email = emailValue, 
                    pass = passwordValue, 
                    phone = phoneValue,
                    name = emailValue.substringBefore("@"),
                    agreed = _privacyAgreed.value
                )
                _isLoading.value = false
                _isLogin.value = true // Переключаемся на вход
                _error.value = "Заявка отправлена, ожидайте одобрения"
                Log.i("AuthViewModel", "Registration successful (201/200) for $emailValue")
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is retrofit2.HttpException -> {
                        val body = e.response()?.errorBody()?.string() ?: ""
                        "Ошибка сервера (${e.code()}): $body"
                    }
                    is java.net.ConnectException -> "Не удалось подключиться к серверу"
                    else -> e.localizedMessage ?: "Неизвестная ошибка"
                }
                Log.e("AuthViewModel", "Registration error: $errorMsg", e)
                _isLoading.value = false
                _error.value = "Ошибка регистрации: $errorMsg"
            }
        }
    }

    fun fetchPendingUsers() {
        viewModelScope.launch {
            try {
                _pendingUsers.value = localApiService.getPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to fetch pending users", e)
            }
        }
    }

    fun approveUser(userUid: String) {
        viewModelScope.launch {
            try {
                localApiService.approveUser(userUid = userUid)
                fetchPendingUsers() // Обновляем список
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to approve user", e)
            }
        }
    }

    /**
     * Поллинг статуса пользователя для уведомлений об активации или удалении.
     */
    private var statusJob: kotlinx.coroutines.Job? = null
    private var lastKnownStatus: String? = null

    fun startStatusPolling(context: Context) {
        val token = _jwtToken.value
        if (token == null || token == "guest_token") return

        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            while (true) {
                try {
                    val response = localApiService.getAuthStatus()
                    val status = response["status"] // "active", "pending", "deleted"
                    
                    if (status != lastKnownStatus && lastKnownStatus != null) {
                        when (status) {
                            "active" -> if (lastKnownStatus == "pending") {
                                com.business.gym.util.NotificationHelper.showNotification(
                                    context,
                                    "Аккаунт активирован",
                                    "Добро пожаловать в GYM ABS! Ваша учетная запись одобрена."
                                )
                            }
                            "deleted" -> {
                                com.business.gym.util.NotificationHelper.showNotification(
                                    context,
                                    "Аккаунт удален",
                                    "Ваша учетная запись была удалена администратором."
                                )
                                signOut()
                                break
                            }
                        }
                    }
                    lastKnownStatus = status
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401 || e.code() == 404) {
                        com.business.gym.util.NotificationHelper.showNotification(
                            context,
                            "Ошибка авторизации",
                            "Ваш аккаунт более не доступен."
                        )
                        signOut()
                        break
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Status polling error", e)
                }
                kotlinx.coroutines.delay(15000) // Раз в 15 секунд
            }
        }
    }

    fun stopStatusPolling() {
        statusJob?.cancel()
        statusJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusPolling()
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
    fun dismissSetPasswordDialog() {}
    fun setPasswordForPhoneUser(p: String, s: () -> Unit) {}
    fun signInWithGoogle(t: String, s: (String) -> Unit) {}
    fun signInWithPhoneAndPassword(s: (String) -> Unit) {}
    fun retryLocalLogin(e: String) { signInWithEmail {} }
}
