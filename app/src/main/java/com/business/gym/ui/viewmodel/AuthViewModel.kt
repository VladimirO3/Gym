package com.business.gym.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.api.LocalUser
import com.business.gym.data.api.LoginRequest
import kotlinx.coroutines.launch

/**
 * ViewModel для управления процессами авторизации через локальный сервер (VPS).
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

    // Используем не-nullable String, чтобы избежать NPE в UI
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
        _jwtToken.value = "guest_token"
        saveSession(getApplication(), GUEST_EMAIL, null, "guest_token", uid = "guest")
        onSuccess()
    }

    private val _otpEmail = mutableStateOf("")
    val otpEmail: State<String> = _otpEmail
    private val _otpPhone = mutableStateOf("")
    val otpPhone: State<String> = _otpPhone
    private val _otpCode = mutableStateOf("")
    val otpCode: State<String> = _otpCode
    private val _authMode = mutableStateOf("email")
    val authMode: State<String> = _authMode
    private val _isPasswordMode = mutableStateOf(false)
    val isPasswordMode: State<Boolean> = _isPasswordMode

    fun togglePasswordMode() { 
        _isPasswordMode.value = !_isPasswordMode.value
        _error.value = null 
    }

    fun onOtpEmailChange(newValue: String) { 
        _otpEmail.value = newValue
        _email.value = newValue 
        _error.value = null 
        if (isStaticAdmin(newValue)) _isPasswordMode.value = true
    }

    fun saveCredentials(email: String, pass: String) {
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
            if (isStaticAdmin(savedEmail) || savedPassword.isNotBlank()) _isPasswordMode.value = true
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
        
        Log.d("AuthViewModel", "Loading session. Token exists: ${savedToken != null}, Saved UID: $savedUid")
        
        if (savedToken != null) {
            _currentUserEmail.value = savedEmail
            _jwtToken.value = savedToken
            _refreshToken.value = savedRefreshToken
            // Fallback: UID -> Email -> Phone -> "user"
            _currentUid.value = savedUid ?: savedEmail ?: savedPhone ?: "user"
            _isGuest.value = savedToken == "guest_token"
            
            // Пытаемся получить профиль и актуальный UID с сервера
            if (savedUid == null && savedToken != "guest_token") {
                fetchAndSaveProfile { /* ignore */ }
            }
        }
    }

    fun saveSession(context: Context, email: String?, phone: String?, token: String, refreshToken: String? = null, uid: String? = null) {
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("user_session_email", email)
            putString("user_session_phone", phone)
            putString("user_session_token", token)
            if (refreshToken != null) putString("user_session_refresh_token", refreshToken)
            if (uid != null) putString("user_session_uid", uid)
            commit()
        }
        Log.d("AuthViewModel", "Session saved. email=$email, uid=$uid")
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun fetchAndSaveProfile(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Fetching profile from server...")
                val profile = localApiService.getProfile()
                val profileUid = profile.uid
                
                if (profileUid != null) {
                    Log.d("AuthViewModel", "Profile UID received: $profileUid")
                    _currentUid.value = profileUid
                    _currentUserEmail.value = profile.email
                    saveSession(getApplication(), profile.email, null, _jwtToken.value!!, _refreshToken.value, profileUid)
                    onSuccess(profile.email)
                } else {
                    // Если сервер прислал null UID, используем текущее значение (email/fallback) и сохраняем его
                    val currentId = _currentUid.value
                    Log.w("AuthViewModel", "Profile UID is null from server, keeping temporary ID: $currentId")
                    saveSession(getApplication(), profile.email, null, _jwtToken.value!!, _refreshToken.value, currentId)
                    onSuccess(profile.email)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to fetch profile from VPS", e)
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
                Log.d("AuthViewModel", "Signing in with email: $emailValue")
                val response = localApiService.login(emailValue, passwordValue)
                _jwtToken.value = response.token
                _refreshToken.value = response.refreshToken
                
                // Устанавливаем данные пользователя СРАЗУ для перехода на главный экран
                _currentUserEmail.value = emailValue
                _currentUid.value = emailValue

                saveSession(getApplication(), emailValue, null, response.token, response.refreshToken, uid = emailValue)
                saveCredentials(emailValue, passwordValue) 
                
                fetchAndSaveProfile { email ->
                    _isLoading.value = false
                    onSuccess(email)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error: ${e.message}", e)
                _isLoading.value = false
                _error.value = "Ошибка входа: ${e.localizedMessage ?: "проверьте данные"}"
            }
        }
    }

    fun retryLocalLogin(emailStr: String? = null, onSuccess: (String) -> Unit = {}) {
        if (emailStr != null) _email.value = emailStr
        signInWithEmail(onSuccess)
    }

    fun requestOtp(activity: Activity? = null) {
        val isEmail = _authMode.value == "email"
        val target = if (isEmail) _otpEmail.value.trim().lowercase() else _otpPhone.value.trim()
        if (target.isBlank()) {
            _error.value = "Введите данные"
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                if (isEmail) localApiService.requestOtp(email = target)
                else localApiService.requestOtp(phone = target)
                _isLoading.value = false
                _error.value = "Код отправлен!"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "OTP request failed", e)
                _isLoading.value = false
                _error.value = "Ошибка отправки кода."
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
                val response = localApiService.verifyOtp(email = email, phone = phone, otp = code)
                _jwtToken.value = response.token
                _refreshToken.value = response.refreshToken
                
                val temporaryUid = email ?: phone ?: "user"
                _currentUserEmail.value = email ?: phone
                _currentUid.value = temporaryUid
                
                saveSession(getApplication(), email, phone, response.token, response.refreshToken, uid = temporaryUid)
                if (email != null) saveCredentials(email, "")

                fetchAndSaveProfile { userEmail ->
                    _isLoading.value = false
                    onSuccess(userEmail)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "OTP verify error: ${e.message}", e)
                _isLoading.value = false
                _error.value = "Неверный код или ошибка связи: ${e.localizedMessage}"
            }
        }
    }

    fun signUpWithEmail(onSuccess: (String) -> Unit) {
        val emailValue = _otpEmail.value.trim().lowercase()
        val phoneValue = _regPhone.value.trim()
        val passwordValue = _password.value
        val confirmValue = _confirmPassword.value
        if (emailValue.isBlank() || phoneValue.isBlank() || passwordValue.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }
        if (passwordValue != confirmValue) {
            _error.value = "Пароли не совпадают"
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                localApiService.register(emailValue, passwordValue, phoneValue, emailValue.substringBefore("@"), _privacyAgreed.value)
                _isLoading.value = false
                _isLogin.value = true
                _error.value = "Заявка отправлена!"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Reg error", e)
                _isLoading.value = false
                _error.value = "Ошибка регистрации."
            }
        }
    }

    fun fetchPendingUsers() {
        viewModelScope.launch {
            try {
                _pendingUsers.value = localApiService.getPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Fetch pending failed", e)
            }
        }
    }

    fun approveUser(userUid: String) {
        viewModelScope.launch {
            try {
                localApiService.approveUser(userUid = userUid)
                fetchPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Approve failed", e)
            }
        }
    }

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
                    val status = response["status"]
                    if (status != lastKnownStatus && lastKnownStatus != null) {
                        if (status == "deleted") {
                            signOut()
                            break
                        }
                    }
                    lastKnownStatus = status
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(15000)
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
        _isGuest.value = false
        clearSession(getApplication())
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
