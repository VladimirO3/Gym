package com.business.gym_app.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.business.gym_app.data.api.LocalUser
import com.business.gym_app.R
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.receiver.ChatAlarmReceiver
import com.business.gym_app.service.ChatForegroundService
import com.business.gym_app.util.AppLanguage
import com.business.gym_app.util.AuthUtils
import com.business.gym_app.util.PinHelper
import com.business.gym_app.util.PasswordHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

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

    private val _currentUserRole = mutableStateOf<String?>("user")
    val currentUserRole: State<String?> = _currentUserRole

    // Используем не-nullable String, чтобы избежать NPE в UI
    private val _currentUid = mutableStateOf("")
    val currentUid: State<String> = _currentUid

    private val _jwtToken = mutableStateOf<String?>(null)
    val jwtToken: State<String?> = _jwtToken

    private val _pendingUsers = mutableStateOf(listOf<LocalUser>())
    val pendingUsers: State<List<LocalUser>> = _pendingUsers
    private var adminAccessDenied = false

    private val _refreshToken = mutableStateOf<String?>(null)
    val refreshToken: State<String?> = _refreshToken

    private val _isGuest = mutableStateOf(false)
    val isGuest: State<Boolean> = _isGuest

    private val _isSessionLoaded = mutableStateOf(false)
    val isSessionLoaded: State<Boolean> = _isSessionLoaded

    // --- Быстрый вход по PIN-коду из 4 цифр ---
    private val _pinMode = mutableStateOf(false)
    val pinMode: State<Boolean> = _pinMode

    private val _pinValue = mutableStateOf("")
    val pinValue: State<String> = _pinValue

    private val _pinConfirm = mutableStateOf("")
    val pinConfirm: State<String> = _pinConfirm

    /** Экран только что зарегистрировался и должен предложить создать PIN. */
    private val _pendingPinSetup = mutableStateOf(false)
    val pendingPinSetup: State<Boolean> = _pendingPinSetup

    /** PIN просрочен (7 дней) — вход по нему запрещён, требуем задать новый. */
    private val _pinExpired = mutableStateOf(false)
    val pinExpired: State<Boolean> = _pinExpired

    /** Причина открытия диалога смены PIN: истёк срок / скоро истечёт / ручная смена. */
    private val _pinChangeReason = mutableStateOf(PinChangeReason.MANUAL)
    val pinChangeReason: State<PinChangeReason> = _pinChangeReason

    enum class PinChangeReason { MANUAL, EXPIRED, EXPIRING_SOON, REQUIRED }

    private val _pinAccount = mutableStateOf("")
    val pinAccount: State<String> = _pinAccount

    fun onPinChange(value: String) {
        _pinValue.value = value.filter { it.isDigit() }.take(PinHelper.PIN_LENGTH)
    }

    fun onPinConfirmChange(value: String) {
        _pinConfirm.value = value.filter { it.isDigit() }.take(PinHelper.PIN_LENGTH)
    }

    fun setPinMode(enabled: Boolean) {
        _pinMode.value = enabled
        if (enabled) {
            _pinValue.value = ""
            _pinConfirm.value = ""
        } else {
            _error.value = null
        }
    }

    /** К какому логину привязан сохранённый PIN (email или телефон). */
    fun pinLoginAccount(context: Context): String {
        val mode = _authMode.value.ifBlank { _registeredMethod.value.orEmpty() }
        val current = when {
            mode == "phone" -> _otpPhone.value
            _email.value.isNotBlank() -> _email.value
            else -> _otpEmail.value
        }
        if (current.isNotBlank()) return current
        return PinHelper.pinAccount(context).orEmpty()
    }

    fun hasPinForCurrentAccount(context: Context): Boolean {
        val account = pinLoginAccount(context)
        if (account.isBlank()) return false
        return PinHelper.isPinForAccount(context, account)
    }

    fun requestPinSetup(account: String, reason: PinChangeReason = PinChangeReason.MANUAL) {
        _pinAccount.value = account
        _pinValue.value = ""
        _pinConfirm.value = ""
        _pinChangeReason.value = reason
        _pendingPinSetup.value = true
    }

    fun dismissPinSetup() {
        _pendingPinSetup.value = false
        _pinValue.value = ""
        _pinConfirm.value = ""
    }

    fun savePinCode(account: String, onDone: () -> Unit) {
        val pin = _pinValue.value
        val confirm = _pinConfirm.value
        when {
            !PinHelper.isValidPinFormat(pin) || !PinHelper.isValidPinFormat(confirm) -> {
                _error.value = res.getString(R.string.pin_enter_4_digits)
                return
            }
            pin != confirm -> {
                _error.value = res.getString(R.string.pin_mismatch)
                return
            }
            account.isBlank() -> {
                _error.value = res.getString(R.string.unknown_error)
                return
            }
            else -> {
                val ok = PinHelper.setPin(getApplication(), account, pin)
                if (!ok) {
                    _error.value = res.getString(R.string.unknown_error)
                    return
                }
                _pinAccount.value = account
                _pendingPinSetup.value = false
                _pinExpired.value = false
                _pinValue.value = ""
                _pinConfirm.value = ""
                _error.value = null
                onDone()
            }
        }
    }

    /**
     * Вход по PIN: проверяем PIN для сохранённого логина и дальше
     * логинимся сохранёнными email/паролем (серверный вход как обычно).
     * Если с создания PIN прошло 7+ дней — вход запрещаем и требуем смену.
     *
     * @return true если PIN принят и идёт серверный вход, false если нужна смена/пароль.
     */
    fun signInWithPin(
        context: Context,
        onSuccess: (String) -> Unit,
        onNeedPassword: () -> Unit = {},
        onPinExpired: () -> Unit = {}
    ): Boolean {
        val account = pinLoginAccount(context)
        val pin = _pinValue.value
        if (account.isBlank() || !hasSavedCredentials()) {
            _error.value = res.getString(R.string.biometric_no_saved_data)
            onNeedPassword()
            return false
        }
        if (PinHelper.isPinExpired(context)) {
            _pinExpired.value = true
            _error.value = res.getString(R.string.pin_expired)
            requestPinSetup(account, PinChangeReason.EXPIRED)
            onPinExpired()
            return false
        }
        if (!PinHelper.isValidPinFormat(pin)) {
            _error.value = res.getString(R.string.pin_enter_4_digits)
            return false
        }
        if (!PinHelper.verifyPin(context, account, pin)) {
            _error.value = res.getString(R.string.pin_wrong)
            _pinValue.value = ""
            return false
        }
        _pinValue.value = ""
        _error.value = null
        if (PinHelper.isPinExpiringSoon(context)) {
            requestPinSetup(account, PinChangeReason.EXPIRING_SOON)
        }
        signInWithEmail(onSuccess)
        return true
    }

    /** Сбросить PIN (например, ссылка "Забыли PIN?" → вход по паролю). */
    fun clearPinCode() {
        PinHelper.clearPin(getApplication())
        _pinValue.value = ""
        _pinConfirm.value = ""
        _pinMode.value = false
        _pinExpired.value = false
    }

    /** Закрыть диалог смены PIN без сохранения (только если срок не истёк). */
    fun dismissPinChange(onDismissed: () -> Unit = {}) {
        // REQUIRED — PIN обязателен для входа, закрыть диалог нельзя.
        if (_pinChangeReason.value == PinChangeReason.REQUIRED) {
            _error.value = res.getString(R.string.pin_create_required)
            return
        }
        if (_pinChangeReason.value == PinChangeReason.EXPIRED || _pinExpired.value) {
            // Просроченный PIN закрыть нельзя — сначала задайте новый.
            _error.value = res.getString(R.string.pin_expired)
            return
        }
        dismissPinSetup()
        _error.value = null
        onDismissed()
    }

    private val localApiService get() = NewsApiService.create(getApplication())

    /**
     * Ресурсы в выбранной локали приложения. ViewModel получает Application-контекст,
     * который не пересоздается при смене языка, поэтому локаль берем через AppLanguage.
     */
    private val res: android.content.res.Resources
        get() = AppLanguage.localized(getApplication()).resources

    companion object {
        const val ADMIN_EMAIL = AuthUtils.ADMIN_EMAIL
        const val GUEST_EMAIL = AuthUtils.GUEST_EMAIL
        
        fun isStaticAdmin(email: String?): Boolean {
            return AuthUtils.isStaticAdmin(email)
        }
    }

    val isRootAdminState = derivedStateOf {
        AuthUtils.isRootAdmin(_currentUserEmail.value)
    }

    val isAdminState = derivedStateOf {
        val email = _currentUserEmail.value
        val role = _currentUserRole.value?.lowercase()

        val result = role == "admin"
        
        if (email != null) {
            Log.d("AuthViewModel", "isAdmin check: email=$email, serverRole=$role -> Result=$result")
        }
        
        result
    }

    fun isAdmin(): Boolean {
        return isAdminState.value
    }

    fun loginAsGuest(onSuccess: () -> Unit) {
        // Сначала сохраняем сессию в Prefs, чтобы интерцепторы видели её
        saveSession(getApplication(), GUEST_EMAIL, null, "guest_token", uid = "guest", role = "guest")

        _isGuest.value = true
        _currentUserEmail.value = GUEST_EMAIL
        _currentUid.value = "guest"
        _jwtToken.value = "guest_token"
        
        // Очищаем ошибки перед входом
        _error.value = null
        
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

    private val _registeredMethod = mutableStateOf<String?>(null)
    val registeredMethod: State<String?> = _registeredMethod

    fun hasSavedCredentials(): Boolean {
        val emailToUse = _email.value.ifBlank { _otpEmail.value }
        val passToUse = _password.value
        return emailToUse.isNotBlank() && passToUse.isNotBlank()
    }

    fun saveCredentials(email: String, pass: String, method: String = "email", phone: String = "") {
        val sharedPref = getApplication<Application>().getSharedPreferences("auth_credentials", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("saved_email", email)
            putString("saved_password", pass)
            putString("registered_method", method)
            if (phone.isNotBlank()) putString("saved_phone", phone)
            apply()
        }
        _registeredMethod.value = method
        if (method.isNotBlank()) {
            _authMode.value = method
        }
    }

    private fun loadCredentials() {
        val sharedPref = getApplication<Application>().getSharedPreferences("auth_credentials", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("saved_email", "") ?: ""
        val savedPassword = sharedPref.getString("saved_password", "") ?: ""
        val savedPhone = sharedPref.getString("saved_phone", "") ?: ""
        val savedMethod = sharedPref.getString("registered_method", null)
            ?: if (savedEmail.isNotBlank()) "email" else null

        _registeredMethod.value = savedMethod
        if (!savedMethod.isNullOrBlank()) {
            _authMode.value = savedMethod
        }
        if (savedEmail.isNotBlank()) {
            _otpEmail.value = savedEmail
            _email.value = savedEmail
            _password.value = savedPassword
            if (isStaticAdmin(savedEmail) || savedPassword.isNotBlank()) _isPasswordMode.value = true
        }
        if (savedPhone.isNotBlank()) {
            _otpPhone.value = savedPhone
        }
    }

    init {
        loadCredentials()
        loadSession(getApplication())
    }

    fun signInWithBiometrics(onSuccess: (String) -> Unit, onNeedPassword: () -> Unit = {}) {
        val mode = _authMode.value.ifBlank { _registeredMethod.value.orEmpty() }
        val emailToUse = when {
            mode == "phone" -> _otpPhone.value
            _email.value.isNotBlank() -> _email.value
            else -> _otpEmail.value
        }
        val passToUse = _password.value
        
        if (emailToUse.isBlank() || passToUse.isBlank()) {
            _error.value = res.getString(R.string.biometric_no_saved_data)
            // Нет сохранённого пароля — биометрия невозможна, дальше только ручной ввод.
            onNeedPassword()
            return
        }
        signInWithEmail(onSuccess)
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
        val savedRole = sharedPref.getString("user_session_role", "user")
        
        if (savedToken != null) {
            // Загружаем токен в память, но НЕ устанавливаем email до проверки профиля
            _jwtToken.value = savedToken
            _refreshToken.value = savedRefreshToken
            _currentUserRole.value = savedRole
            _isGuest.value = savedToken == "guest_token"
            
            if (savedToken != "guest_token") {
                // Пытаемся подтвердить сессию на сервере
                fetchAndSaveProfile { email, uid ->
                    _currentUserEmail.value = email
                    _currentUid.value = uid
                    _isSessionLoaded.value = true
                }
            } else {
                // Для гостя сессия всегда валидна
                _currentUserEmail.value = GUEST_EMAIL
                _currentUid.value = "guest"
                _currentUserRole.value = "guest"
                _isSessionLoaded.value = true
            }
        } else {
            _isSessionLoaded.value = true
        }
    }

    fun saveSession(context: Context, email: String?, phone: String?, token: String, refreshToken: String? = null, uid: String? = null, role: String? = "user") {
        Log.d("AuthViewModel", "saveSession: email=$email, uid=$uid, role=$role")
        val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("user_session_email", email)
            putString("user_session_phone", phone)
            putString("user_session_token", token)
            if (refreshToken != null) putString("user_session_refresh_token", refreshToken)
            if (uid != null) putString("user_session_uid", uid)
            if (role != null) putString("user_session_role", role)
            commit() // Используем commit для немедленной записи
        }
        _currentUserRole.value = role
        _currentUserEmail.value = email
        if (uid != null) _currentUid.value = uid
        
        Log.d("AuthViewModel", "Session updated in memory: email=$email, role=$role")

        // После входа сразу планируем фоновую проверку сообщений: иначе уведомления
        // придут только после следующего запуска приложения.
        if (token != "guest_token") {
            try {
                ChatAlarmReceiver.schedule(context)
                com.business.gym_app.service.ChatCheckWorker.schedule(context)
            } catch (e: Exception) {
                Log.w("AuthViewModel", "Failed to schedule chat checks: ${e.message}")
            }
        }
    }

    fun clearSession(context: Context) {
        context.stopService(Intent(context, ChatForegroundService::class.java))
        com.business.gym_app.service.ChatCheckWorker.cancel(context)
        ChatAlarmReceiver.cancel(context)
        com.business.gym_app.util.ChatUnreadNotifier.clear(context)
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun fetchAndSaveProfile(onSuccess: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Fetching profile from server...")
                val token = _jwtToken.value
                    ?: run {
                        Log.w("AuthViewModel", "Profile refresh skipped: session has no JWT")
                        stopStatusPolling()
                        clearSession(getApplication())
                        _isSessionLoaded.value = true
                        return@launch
                    }
                if (token == "guest_token") {
                    stopStatusPolling()
                    return@launch
                }
                val profile = localApiService.getProfileWithToken("Bearer $token")
                Log.d("AuthViewModel", "Profile received: email=${profile.email}, role=${profile.role}, isAdmin=${profile.isAdmin}")
                
                // Умный поиск UID: приоритет ID > UID > Email
                var profileUid = profile.id?.toString() ?: profile.uid ?: profile.email
                
                if (profileUid.isBlank() && isStaticAdmin(profile.email)) {
                    profileUid = "1"
                }
                
                if (profileUid.isNotBlank()) {
                    Log.d("AuthViewModel", "Profile UID resolved: $profileUid")
                    
                    // Улучшенное определение роли: игнорируем регистр и проверяем оба поля
                    val rawRole = profile.role?.toString()?.trim()?.lowercase() ?: ""
                    val isAdminVal = when(profile.isAdmin) {
                        is Boolean -> profile.isAdmin
                        is Number -> profile.isAdmin.toInt() == 1
                        is String -> profile.isAdmin.lowercase() == "true" || profile.isAdmin == "1"
                        else -> false
                    }
                    
                    val isStatic = AuthUtils.isStaticAdmin(profile.email)
                    val resolvedRole = if (
                        rawRole == "admin" ||
                        rawRole == "administrator" ||
                        rawRole == "root" ||
                        isAdminVal
                    ) "admin" else "user"
                    
                    Log.d("AuthViewModel", "Resolved role: $resolvedRole (raw role: ${profile.role}, isAdmin: $isAdminVal, isStatic: $isStatic)")

                    saveSession(getApplication(), profile.email, null, _jwtToken.value!!, _refreshToken.value, profileUid, resolvedRole)
                    onSuccess(profile.email, profileUid)
                } else {
                    Log.e("AuthViewModel", "User ID is empty after fetching profile")
                    clearSession(getApplication())
                    signOut()
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to fetch profile from VPS", e)
                if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                    clearSession(getApplication())
                    signOut()
                } else {
                    val prefs = getApplication<Application>()
                        .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    _currentUserEmail.value = prefs.getString("user_session_email", null)
                    _currentUid.value = prefs.getString("user_session_uid", _currentUserEmail.value ?: "")
                        ?: ""
                    _isSessionLoaded.value = true
                }
            }
        }
    }

    fun signInWithEmail(onSuccess: (String) -> Unit) {
        val identifier = if (_authMode.value == "email") _email.value.trim().lowercase() else _otpPhone.value.trim()
        val passwordValue = _password.value

        if (identifier.isBlank() || passwordValue.isBlank()) {
            _error.value = res.getString(R.string.fill_all_fields)
            return
        }
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Signing in with identifier: $identifier")
                val response = localApiService.login(identifier, passwordValue)
                
                // НЕ сохраняем сессию до подтверждения профиля
                val token = response.token
                val refresh = response.refreshToken
                
                // Проверяем профиль ПЕРЕД окончательным входом
                try {
                    val profile = localApiService.getProfileWithToken("Bearer $token")
                    
                    var profileUid = profile.id?.toString() ?: profile.uid ?: profile.email
                    
                    if (profileUid.isBlank() && isStaticAdmin(profile.email)) {
                        profileUid = "1"
                    }
                    
                    if (profileUid.isBlank()) {
                        _isLoading.value = false
                        _error.value = res.getString(R.string.user_id_not_received)
                        return@launch
                    }
                    
                    val rawRole = profile.role?.toString()?.trim()?.lowercase() ?: ""
                    val isAdminVal = when(profile.isAdmin) {
                        is Boolean -> profile.isAdmin
                        is Number -> profile.isAdmin.toInt() == 1
                        is String -> profile.isAdmin.lowercase() == "true" || profile.isAdmin == "1"
                        else -> false
                    }
                    
                    val isStatic = isStaticAdmin(profile.email)
                    val resolvedRole = if (
                        rawRole == "admin" ||
                        rawRole == "administrator" ||
                        rawRole == "root" ||
                        isAdminVal
                    ) "admin" else "user"

                    saveSession(getApplication(), profile.email, if (_authMode.value == "phone") identifier else null, token, refresh, profileUid, resolvedRole)
                    saveCredentials(
                        if (_authMode.value == "email") identifier else profile.email,
                        passwordValue,
                        _authMode.value.ifBlank { "email" },
                        if (_authMode.value == "phone") identifier else ""
                    )

                    _jwtToken.value = token
                    _refreshToken.value = refresh
                    _currentUserEmail.value = profile.email
                    _currentUid.value = profileUid
                    _currentUserRole.value = resolvedRole

                    _isLoading.value = false
                    onSuccess(profile.email)
                } catch (pe: Exception) {
                    Log.e("AuthViewModel", "Profile validation failed after login", pe)
                    
                    if (isStaticAdmin(identifier)) {
                        Log.w("AuthViewModel", "Admin login allowed with fallback UID")
                        val fallbackUid = "1"
                        
                        saveSession(getApplication(), identifier, if (_authMode.value == "phone") identifier else null, token, refresh, fallbackUid, "admin")
                        saveCredentials(
                            identifier,
                            passwordValue,
                            _authMode.value.ifBlank { "email" },
                            if (_authMode.value == "phone") identifier else ""
                        )

                        _jwtToken.value = token
                        _refreshToken.value = refresh
                        _currentUserEmail.value = identifier
                        _currentUid.value = fallbackUid
                        _currentUserRole.value = "admin"
                        
                        _isLoading.value = false
                        onSuccess(identifier)
                    } else {
                        // Login already authenticated the user. Do not discard a valid
                        // session only because the optional profile request is unavailable.
                        val fallbackUid = identifier
                        saveSession(
                            getApplication(),
                            identifier,
                            if (_authMode.value == "phone") identifier else null,
                            token,
                            refresh,
                            fallbackUid,
                            "user"
                        )
                        saveCredentials(
                            identifier,
                            passwordValue,
                            _authMode.value.ifBlank { "email" },
                            if (_authMode.value == "phone") identifier else ""
                        )
                        _jwtToken.value = token
                        _refreshToken.value = refresh
                        _currentUserEmail.value = identifier
                        _currentUid.value = fallbackUid
                        _currentUserRole.value = "user"
                        _isLoading.value = false
                        onSuccess(identifier)
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error: ${e.message}", e)
                _isLoading.value = false
                
                if (e is retrofit2.HttpException) {
                    when (e.code()) {
                        401, 404 -> _error.value = res.getString(R.string.invalid_login_or_password)
                        403 -> _error.value = res.getString(R.string.access_blocked)
                        else -> _error.value = res.getString(R.string.server_error_code, e.code())
                    }
                } else {
                    _error.value = res.getString(R.string.login_error_check_internet)
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
            _error.value = res.getString(R.string.enter_data)
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                if (isEmail) localApiService.requestOtp(email = target)
                else localApiService.requestOtp(phone = target)
                _isLoading.value = false
                _error.value = res.getString(R.string.auth_otp_sent)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "OTP request failed", e)
                _isLoading.value = false
                _error.value = res.getString(R.string.send_code_error)
            }
        }
    }

    fun verifyOtp(context: Context, onSuccess: (String) -> Unit) {
        if (_otpCode.value.isBlank()) {
            _error.value = res.getString(R.string.auth_enter_code)
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
                        _error.value = res.getString(R.string.user_id_not_received)
                        return@launch
                    }

                    val rawRole = profile.role?.toString()?.trim()?.lowercase() ?: ""
                    val isAdminVal = when(profile.isAdmin) {
                        is Boolean -> profile.isAdmin
                        is Number -> profile.isAdmin.toInt() == 1
                        is String -> profile.isAdmin.lowercase() == "true" || profile.isAdmin == "1"
                        else -> false
                    }
                    val resolvedRole = if (
                        rawRole == "admin" ||
                        rawRole == "administrator" ||
                        rawRole == "root" ||
                        isAdminVal
                    ) "admin" else "user"

                    saveSession(getApplication(), profile.email, phone, token, refresh, profileUid, resolvedRole)
                    if (email != null) saveCredentials(email, "")

                    _jwtToken.value = token
                    _refreshToken.value = refresh
                    _currentUserEmail.value = profile.email
                    _currentUid.value = profileUid
                    _currentUserRole.value = resolvedRole

                    _isLoading.value = false
                    onSuccess(profile.email)
                } catch (pe: Exception) {
                    Log.e("AuthViewModel", "Profile validation failed after OTP verify", pe)
                    
                    // Резервный вход для админа
                    val target = email ?: phone
                    if (isStaticAdmin(target)) {
                        val fallbackUid = "1"
                        
                        saveSession(getApplication(), target ?: "", phone, token, refresh, fallbackUid, "admin")

                        _jwtToken.value = token
                        _refreshToken.value = refresh
                        _currentUserEmail.value = target
                        _currentUid.value = fallbackUid
                        _currentUserRole.value = "admin"

                        _isLoading.value = false
                        onSuccess(target ?: "")
                    } else {
                        _isLoading.value = false
                        _error.value = res.getString(R.string.profile_check_error)
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "OTP verify error: ${e.message}", e)
                _isLoading.value = false
                
                if (e is retrofit2.HttpException) {
                    when (e.code()) {
                        401, 404 -> _error.value = res.getString(R.string.user_not_exists_or_deleted)
                        403 -> _error.value = res.getString(R.string.code_error_or_user_blocked)
                        else -> _error.value = res.getString(R.string.server_error_code, e.code())
                    }
                } else {
                    _error.value = res.getString(R.string.invalid_code_or_network_error)
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
            _error.value = res.getString(R.string.fill_all_fields)
            return
        }
        
        // 2. Валидация формата Email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            _error.value = res.getString(R.string.invalid_email_format)
            return
        }

        // 3. Валидация длины пароля (например, минимум 6 символов)
        if (passwordValue.length < 6) {
            _error.value = res.getString(R.string.password_min_length)
            return
        }

        if (passwordValue != confirmValue) {
            _error.value = res.getString(R.string.passwords_do_not_match)
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                localApiService.register(emailValue, passwordValue, phoneValue, emailValue.substringBefore("@"), _privacyAgreed.value)
                saveCredentials(emailValue, passwordValue, "email")
                // Только что установленный пароль: 21-дневный срок отсчитывается с него.
                PasswordHelper.markPasswordChanged(getApplication(), emailValue)
                _isLoading.value = false
                _isLogin.value = true
                _error.value = res.getString(R.string.application_sent)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Reg error", e)
                _isLoading.value = false
                
                if (e is retrofit2.HttpException) {
                    when (e.code()) {
                        409 -> _error.value = res.getString(R.string.email_already_registered)
                        400 -> _error.value = res.getString(R.string.registration_data_error)
                        else -> _error.value = res.getString(R.string.server_error_code, e.code())
                    }
                } else {
                    _error.value = res.getString(R.string.registration_connection_error)
                }
            }
        }
    }

    fun fetchPendingUsers() {
        if (adminAccessDenied) {
            Log.d("AuthViewModel", "Skipping pending users request: server denied admin access")
            return
        }
        val token = _jwtToken.value
        if (token.isNullOrBlank() || token == "guest_token") {
            Log.d("AuthViewModel", "Skipping pending users request: guest or unauthenticated user")
            return
        }
        viewModelScope.launch {
            try {
                // The cached role can be stale while the session profile is refreshing.
                // Always verify the current server-side role before calling an admin endpoint.
                val profile = try {
                    localApiService.getProfile()
                } catch (e: Exception) {
                    if (e is HttpException && e.code() == 403) {
                        adminAccessDenied = true
                        _currentUserRole.value = "user"
                        getApplication<Application>()
                            .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("user_session_role", "user")
                            .apply()
                        Log.w("AuthViewModel", "Access denied when getting profile (403), setting role to user")
                        return@launch
                    }
                    Log.w("AuthViewModel", "Failed to fetch profile before pending users check", e)
                    null
                }

                if (profile != null) {
                    val rawRole = profile.role?.toString()?.trim()?.lowercase() ?: ""
                    val isAdminValue = when (profile.isAdmin) {
                        is Boolean -> profile.isAdmin
                        is Number -> profile.isAdmin.toInt() == 1
                        is String -> profile.isAdmin.equals("true", ignoreCase = true) || profile.isAdmin == "1"
                        else -> false
                    }
                    val serverGrantsAdmin = rawRole == "admin" ||
                        rawRole == "administrator" ||
                        rawRole == "root" ||
                        isAdminValue

                    _currentUserRole.value = if (serverGrantsAdmin) "admin" else "user"
                    getApplication<Application>()
                        .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("user_session_role", _currentUserRole.value)
                        .apply()

                    if (!serverGrantsAdmin) {
                        Log.w(
                            "AuthViewModel",
                            "Skipping pending users request: server role='$rawRole', isAdmin=$isAdminValue"
                        )
                        return@launch
                    }
                }

                Log.d("AuthViewModel", "Fetching pending users...")
                _pendingUsers.value = localApiService.getPendingUsers()
                Log.d("AuthViewModel", "Successfully fetched ${pendingUsers.value.size} pending users")
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 403) {
                    adminAccessDenied = true
                    _currentUserRole.value = "user"
                    getApplication<Application>()
                        .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("user_session_role", "user")
                        .apply()
                    Log.w("AuthViewModel", "403 on fetchPendingUsers: user lacks admin permissions on server")
                } else {
                    Log.e("AuthViewModel", "Fetch pending failed", e)
                }
            }
        }
    }

    fun approveUser(user: LocalUser) {
        viewModelScope.launch {
            try {
                val idToApprove = user.id?.toString() ?: user.uid ?: ""
                if (idToApprove.isBlank()) return@launch
                
                Log.d("AuthViewModel", "Approving user: $idToApprove (${user.email})")
                localApiService.approveUser(userUid = idToApprove)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.user_approved), Toast.LENGTH_SHORT).show()
                }
                
                fetchPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Approve failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.approval_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
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

    /**
     * Назначение пользователя администратором.
     */
    fun makeAdmin(user: LocalUser) {
        viewModelScope.launch {
            try {
                val userId = user.id ?: user.uid ?: ""
                val userEmail = user.email
                
                if (userId.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.user_id_not_found), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("AuthViewModel", "Promoting user to admin. ID: $userId, Email: $userEmail")
                
                // 1. Пытаемся обновить через специализированный метод
                try {
                    localApiService.makeAdmin(uid = userId, email = userEmail)
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Specialized makeAdmin failed, trying generic update", e)
                }

                // 2. Пытаемся обновить через общий метод обновления профиля (для надежности)
                val updateBody = mutableMapOf<String, Any>(
                    "role" to "admin",
                    "is_admin" to true
                )
                // Если имя не содержит "админ-", добавим для наглядности
                if (!user.name.startsWith("админ-", ignoreCase = true)) {
                    updateBody["name"] = "админ-${user.name}"
                }

                localApiService.adminUpdateProfile(userId = userId, body = updateBody)

                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.admin_granted), Toast.LENGTH_SHORT).show()
                }

                delay(1000)
                fetchPendingUsers()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Make admin critical failure", e)
                val errorMessage = e.message ?: res.getString(R.string.unknown_error)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.server_error, errorMessage), Toast.LENGTH_LONG).show()
                }
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
            var iterations = 0
            while (isActive) {
                if (_jwtToken.value.isNullOrBlank() || _jwtToken.value == "guest_token") {
                    Log.d("AuthViewModel", "Stopping status polling: JWT is no longer available")
                    break
                }
                try {
                    val response = localApiService.getAuthStatus()
                    val status = response["status"]
                    
                    if (status != lastKnownStatus && lastKnownStatus != null) {
                        if (status == "deleted") {
                            viewModelScope.launch(Dispatchers.Main) {
                                Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.account_deleted_by_admin), Toast.LENGTH_LONG).show()
                            }
                            signOut()
                            break
                        }
                    }
                    lastKnownStatus = status

                    // Каждые 30 секунд (каждые 2 итерации) обновляем профиль полностью,
                    // чтобы подхватить смену роли (например, назначение админом)
                    if (iterations % 2 == 0) {
                        Log.d("AuthViewModel", "Polling: Refreshing profile to check for role updates...")
                        fetchAndSaveProfile { _, _ -> }
                    }
                    iterations++

                } catch (e: Exception) {
                    if (e is retrofit2.HttpException && e.code() == 401) {
                        Log.w("AuthViewModel", "Status check returned 401, possible session expiry")
                    }
                }
                delay(15000)
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
        stopStatusPolling()
        adminAccessDenied = false
        _isLoading.value = false
        _currentUserEmail.value = null
        _jwtToken.value = null
        _refreshToken.value = null
        _pinMode.value = false
        _pinValue.value = ""
        _pinConfirm.value = ""
        _pinExpired.value = false
        _pinChangeReason.value = PinChangeReason.MANUAL
        _pendingPinSetup.value = false
        _currentUid.value = ""
        _isGuest.value = false
        _isSessionLoaded.value = true // После выхода сессия "загружена" (её нет)
        clearSession(getApplication())
    }

    /**
     * Удаление текущего аккаунта (по требованию Google Play)
     */
    fun deleteAccount(onSuccess: () -> Unit) {
        val token = _jwtToken.value
        if (token == null || token == "guest_token") {
            signOut()
            onSuccess()
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                localApiService.deleteAccount()
                signOut()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.account_deleted), Toast.LENGTH_LONG).show()
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Account deletion failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication<Application>(), getApplication<Application>().getString(com.business.gym_app.R.string.account_delete_error), Toast.LENGTH_LONG).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
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
