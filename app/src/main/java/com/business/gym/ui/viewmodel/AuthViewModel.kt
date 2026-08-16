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
import com.business.gym.util.AuthUtils
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

    private val _isSessionLoaded = mutableStateOf(false)
    val isSessionLoaded: State<Boolean> = _isSessionLoaded

    private val localApiService get() = NewsApiService.create(getApplication())

    companion object {
        const val ADMIN_EMAIL = AuthUtils.ADMIN_EMAIL
        const val GUEST_EMAIL = AuthUtils.GUEST_EMAIL
        
        fun isStaticAdmin(email: String?): Boolean {
            return AuthUtils.isStaticAdmin(email)
        }
    }

    fun isAdmin(): Boolean = isStaticAdmin(_currentUserEmail.value)

    fun loginAsGuest(onSuccess: () -> Unit) {
        _isGuest.value = true
        _currentUserEmail.value = GUEST_EMAIL
        _currentUid.value = "guest"
        _jwtToken.value = "guest_token"
        
        // Очищаем ошибки перед входом
        _error.value = null
        
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
    fun onEmailChange(newValue: String) { 
        _email.value = newValue
        _error.value = null 
        if (isStaticAdmin(newValue)) _isPasswordMode.value = true
    }
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
        val savedToken = sharedPref.getString("user_session_token", null)
        val savedRefreshToken = sharedPref.getString("user_session_refresh_token", null)
        val savedUid = sharedPref.getString("user_session_uid", null)
        
        if (savedToken != null) {
            // Загружаем токен в память, но НЕ устанавливаем email до проверки профиля
            _jwtToken.value = savedToken
            _refreshToken.value = savedRefreshToken
            _isGuest.value = savedToken == "guest_token"
            
            if (savedToken != "guest_token") {
                // Пытаемся подтвердить сессию на сервере
                fetchAndSaveProfile { email ->
                    _currentUserEmail.value = email
                    // Мы НЕ устанавливаем UID из email или констант, только из проверенного источника (savedUid или ответ сервера)
                    // Если в SharedPreferences UID был email-ом, он обновится при fetchAndSaveProfile (внутри saveSession)
                    _currentUid.value = savedUid ?: ""
                    _isSessionLoaded.value = true
                }
            } else {
                // Для гостя сессия всегда валидна
                _currentUserEmail.value = GUEST_EMAIL
                _currentUid.value = "guest"
                _isSessionLoaded.value = true
            }
        } else {
            _isSessionLoaded.value = true
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
            commit() // Используем commit для немедленной записи
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
                
                // Умный поиск UID: приоритет ID > UID > Email
                // Используем ID как основной UID, так как числовые идентификаторы надежнее работают в путях URL
                var profileUid = profile.id?.toString() ?: profile.uid ?: profile.email
                
                // Резервный ID для админа, если сервер не прислал его
                if (profileUid.isBlank() && isStaticAdmin(profile.email)) {
                    profileUid = "1"
                    Log.d("AuthViewModel", "Using fallback UID '1' for static admin")
                }
                
                if (profileUid.isNotBlank()) {
                    Log.d("AuthViewModel", "Profile UID resolved: $profileUid")
                    // Сохраняем проверенные данные в сессию
                    saveSession(getApplication(), profile.email, null, _jwtToken.value!!, _refreshToken.value, profileUid)
                    onSuccess(profile.email)
                } else {
                    Log.e("AuthViewModel", "User ID is empty after fetching profile")
                    clearSession(getApplication())
                    signOut()
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to fetch profile from VPS, signing out", e)
                // Если мы админ, пробуем остаться в системе даже при ошибке профиля (сетевой сбой)
                if (isStaticAdmin(_currentUserEmail.value)) {
                    Log.w("AuthViewModel", "Admin profile fetch failed, but keeping session due to network error")
                    onSuccess(_currentUserEmail.value ?: ADMIN_EMAIL)
                } else {
                    clearSession(getApplication())
                    signOut()
                }
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
                
                // НЕ сохраняем сессию до подтверждения профиля, чтобы избежать входа несуществующих пользователей
                val token = response.token
                val refresh = response.refreshToken
                
                // Проверяем профиль ПЕРЕД окончательным входом
                try {
                    val profile = localApiService.getProfileWithToken("Bearer $token")
                    
                    // Умный поиск UID: приоритет ID > UID > Email
                    var profileUid = profile.id?.toString() ?: profile.uid ?: profile.email
                    
                    // Резервный ID для админа, если все равно пусто (маловероятно)
                    if (profileUid.isBlank() && isStaticAdmin(emailValue)) {
                        profileUid = "1"
                    }
                    
                    if (profileUid.isBlank()) {
                        _isLoading.value = false
                        _error.value = "Ошибка: ID пользователя не получен с сервера."
                        return@launch
                    }
                    
                    _jwtToken.value = token
                    _refreshToken.value = refresh
                    _currentUserEmail.value = profile.email
                    _currentUid.value = profileUid

                    saveSession(getApplication(), profile.email, null, token, refresh, profileUid)
                    saveCredentials(emailValue, passwordValue)
                    
                    _isLoading.value = false
                    onSuccess(profile.email)
                } catch (pe: Exception) {
                    Log.e("AuthViewModel", "Profile validation failed after login", pe)
                    
                    // Если это админ, разрешаем вход даже при ошибке получения профиля
                    if (isStaticAdmin(emailValue)) {
                        Log.w("AuthViewModel", "Admin login allowed with fallback UID due to profile error")
                        val fallbackUid = "1"
                        _jwtToken.value = token
                        _refreshToken.value = refresh
                        _currentUserEmail.value = emailValue
                        _currentUid.value = fallbackUid
                        saveSession(getApplication(), emailValue, null, token, refresh, fallbackUid)
                        saveCredentials(emailValue, passwordValue)
                        _isLoading.value = false
                        onSuccess(emailValue)
                    } else {
                        _isLoading.value = false
                        _error.value = "Ошибка проверки профиля. Возможно, аккаунт еще не активирован."
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error: ${e.message}", e)
                _isLoading.value = false
                
                if (e is retrofit2.HttpException) {
                    when (e.code()) {
                        401, 404 -> _error.value = "Такого пользователя не существует или он был удален"
                        403 -> _error.value = "Ваша учетная запись заблокирована или ожидает подтверждения"
                        else -> _error.value = "Ошибка сервера: ${e.code()}"
                    }
                } else {
                    _error.value = "Ошибка входа: проверьте интернет-соединение"
                }
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
                val token = response.token
                val refresh = response.refreshToken

                // Проверка профиля перед входом
                try {
                    val profile = localApiService.getProfileWithToken("Bearer $token")
                    
                    // Умный поиск UID: приоритет ID > UID > Email
                    var profileUid = profile.id?.toString() ?: profile.uid ?: profile.email

                    // Резервный ID для админа
                    if (profileUid.isBlank() && isStaticAdmin(email ?: phone)) {
                        profileUid = "1"
                    }

                    if (profileUid.isBlank()) {
                        _isLoading.value = false
                        _error.value = "Ошибка: ID пользователя не получен с сервера."
                        return@launch
                    }

                    _jwtToken.value = token
                    _refreshToken.value = refresh
                    _currentUserEmail.value = profile.email
                    _currentUid.value = profileUid

                    saveSession(getApplication(), profile.email, phone, token, refresh, profileUid)
                    if (email != null) saveCredentials(email, "")

                    _isLoading.value = false
                    onSuccess(profile.email)
                } catch (pe: Exception) {
                    Log.e("AuthViewModel", "Profile validation failed after OTP verify", pe)
                    
                    // Резервный вход для админа
                    val target = email ?: phone
                    if (isStaticAdmin(target)) {
                        val fallbackUid = "1"
                        _jwtToken.value = token
                        _refreshToken.value = refresh
                        _currentUserEmail.value = target
                        _currentUid.value = fallbackUid
                        saveSession(getApplication(), target, phone, token, refresh, fallbackUid)
                        _isLoading.value = false
                        onSuccess(target ?: "")
                    } else {
                        _isLoading.value = false
                        _error.value = "Ошибка проверки профиля. Возможно, аккаунт удален или не подтвержден."
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "OTP verify error: ${e.message}", e)
                _isLoading.value = false
                
                if (e is retrofit2.HttpException) {
                    when (e.code()) {
                        401, 404 -> _error.value = "Такого пользователя не существует или он был удален"
                        403 -> _error.value = "Ошибка кода или пользователь заблокирован"
                        else -> _error.value = "Ошибка сервера: ${e.code()}"
                    }
                } else {
                    _error.value = "Неверный код или ошибка связи"
                }
            }
        }
    }

    fun signUpWithEmail(onSuccess: (String) -> Unit) {
        val emailValue = _otpEmail.value.trim().lowercase()
        val phoneValue = _regPhone.value.trim()
        val passwordValue = _password.value
        val confirmValue = _confirmPassword.value
        
        // 1. Базовая валидация пустых полей
        if (emailValue.isBlank() || phoneValue.isBlank() || passwordValue.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }
        
        // 2. Валидация формата Email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            _error.value = "Неверный формат Email"
            return
        }

        // 3. Валидация длины пароля (например, минимум 6 символов)
        if (passwordValue.length < 6) {
            _error.value = "Пароль должен быть не менее 6 символов"
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
                
                if (e is retrofit2.HttpException) {
                    when (e.code()) {
                        409 -> _error.value = "Пользователь с таким Email уже зарегистрирован"
                        400 -> _error.value = "Ошибка в данных. Проверьте правильность заполнения"
                        else -> _error.value = "Ошибка сервера: ${e.code()}"
                    }
                } else {
                    _error.value = "Ошибка регистрации. Проверьте соединение"
                }
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

    fun approveUser(user: LocalUser) {
        viewModelScope.launch {
            try {
                // Используем числовой ID, если он есть, иначе UID
                val idToApprove = user.id?.toString() ?: user.uid ?: ""
                if (idToApprove.isBlank()) return@launch
                
                localApiService.approveUser(userUid = idToApprove)
                fetchPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Approve failed", e)
            }
        }
    }

    /**
     * Удаление пользователя (отклонение заявки или удаление из системы).
     */
    fun deleteUser(user: LocalUser) {
        viewModelScope.launch {
            try {
                // Используем числовой ID, если он есть, иначе UID
                val idToDelete = user.id?.toString() ?: user.uid ?: ""
                if (idToDelete.isBlank()) return@launch

                localApiService.deleteUser(idToDelete)
                fetchPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Delete failed", e)
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
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    getApplication(), 
                                    "Ваш аккаунт был удален администратором", 
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            signOut()
                            break
                        }
                    }
                    lastKnownStatus = status
                } catch (e: Exception) {
                    if (e is retrofit2.HttpException && e.code() == 401) {
                        Log.w("AuthViewModel", "Status check returned 401, possible session expiry or invalid token")
                    }
                }
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
        _isLoading.value = false
        _currentUserEmail.value = null
        _jwtToken.value = null
        _refreshToken.value = null
        _currentUid.value = ""
        _isGuest.value = false
        _isSessionLoaded.value = true // После выхода сессия "загружена" (её нет)
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
