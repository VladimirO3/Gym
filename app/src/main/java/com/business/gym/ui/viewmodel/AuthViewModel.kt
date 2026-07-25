package com.business.gym.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.UserProfile
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

/**
 * ViewModel для управления процессами авторизации (Email, Телефон, Google).
 */
class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Поля ввода для экранов авторизации
    private val _email = mutableStateOf("")
    val email: State<String> = _email

    private val _password = mutableStateOf("")
    val password: State<String> = _password

    private val _confirmPassword = mutableStateOf("")
    val confirmPassword: State<String> = _confirmPassword

    private val _phoneNumber = mutableStateOf("")
    val phoneNumber: State<String> = _phoneNumber

    private val _otpCode = mutableStateOf("")
    val otpCode: State<String> = _otpCode

    // ID верификации для SMS-авторизации
    private val _verificationId = mutableStateOf<String?>(null)
    val verificationId: State<String?> = _verificationId

    // Режим авторизации: "email" или "phone"
    private val _authMode = mutableStateOf("email")
    val authMode: State<String> = _authMode

    // Флаг: вход (true) или регистрация (false)
    private val _isLogin = mutableStateOf(true)
    val isLogin: State<Boolean> = _isLogin

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // Данные текущего авторизованного пользователя
    private val _currentUserProfile = mutableStateOf<UserProfile?>(null)
    val currentUserProfile: State<UserProfile?> = _currentUserProfile

    private val _currentUserEmail = mutableStateOf(auth.currentUser?.email ?: auth.currentUser?.phoneNumber)
    val currentUserEmail: State<String?> = _currentUserEmail

    private val _currentUid = mutableStateOf(auth.currentUser?.uid ?: "")
    val currentUid: State<String> = _currentUid

    // Флаги управления диалогами
    private val _showSetPasswordDialog = mutableStateOf(false)
    val showSetPasswordDialog: State<Boolean> = _showSetPasswordDialog

    private val _loginWithPasswordMode = mutableStateOf(false)
    val loginWithPasswordMode: State<Boolean> = _loginWithPasswordMode

    init {
        // Слушатель состояния авторизации: срабатывает при входе/выходе
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _currentUserEmail.value = user.email ?: user.phoneNumber
                _currentUid.value = user.uid
                fetchUserProfile(user.uid)
            } else {
                _currentUserEmail.value = null
                _currentUid.value = ""
                _currentUserProfile.value = null
            }
        }
    }

    /**
     * Загрузка профиля пользователя из Firestore или Realtime Database.
     */
    private fun fetchUserProfile(uid: String) {
        firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.toObject(UserProfile::class.java)
            if (profile != null) {
                _currentUserProfile.value = profile
            } else {
                // Если в Firestore нет, ищем в RTDB (обратная совместимость)
                database.getReference("users").child(uid).get().addOnSuccessListener { rtdbSnapshot ->
                    val rtdbProfile = rtdbSnapshot.getValue(UserProfile::class.java)
                    _currentUserProfile.value = rtdbProfile
                }
            }
        }
    }

    companion object {
        const val ADMIN_EMAIL = "verso0100@gmail.com"
        const val ADMIN_PHONE = "+79530481451"

        /**
         * Проверка, является ли пользователь администратором по e-mail или телефону.
         */
        fun isStaticAdmin(emailOrPhone: String?): Boolean {
            if (emailOrPhone == null) return false
            val clean = emailOrPhone.trim().lowercase()
            val phoneDigits = ADMIN_PHONE.replace("+", "")
            return clean == ADMIN_EMAIL.lowercase() || 
                   clean == ADMIN_PHONE ||
                   (clean.contains(phoneDigits) && clean.contains("@phone.gym"))
        }
    }

    fun isAdmin(): Boolean {
        return isStaticAdmin(_currentUserEmail.value)
    }

    /**
     * Восстановление сессии из SharedPreferences.
     */
    fun loadSession(context: Context) {
        if (_currentUserEmail.value == null) {
            val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPref.getString("user_session_email", null)
            val savedTimestamp = sharedPref.getLong("user_session_timestamp", 0L)
            // Сессия живет 3 дня
            if (savedEmail != null && (System.currentTimeMillis() - savedTimestamp) < 3 * 24 * 60 * 60 * 1000L) {
                _currentUserEmail.value = savedEmail
            }
        }
    }

    fun saveSession(context: Context, email: String) {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("user_session_email", email)
            .putLong("user_session_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit().remove("user_session_email").remove("user_session_timestamp").apply()
    }

    // Обработчики ввода
    fun onEmailChange(newValue: String) { _email.value = newValue; _error.value = null }
    fun onPasswordChange(newValue: String) { _password.value = newValue; _error.value = null }
    fun onConfirmPasswordChange(newValue: String) { _confirmPassword.value = newValue; _error.value = null }
    fun onPhoneNumberChange(newValue: String) { 
        var formatted = newValue.filter { it.isDigit() || it == '+' }
        if (formatted.startsWith("8") && !formatted.startsWith("+")) {
            formatted = "+7" + formatted.substring(1)
        }
        _phoneNumber.value = formatted
        _error.value = null 
    }
    fun onOtpCodeChange(newValue: String) { _otpCode.value = newValue; _error.value = null }
    fun setAuthMode(mode: String) { 
        _authMode.value = mode
        _error.value = null 
        _loginWithPasswordMode.value = false
    }
    fun toggleIsLogin() { _isLogin.value = !_isLogin.value }
    fun toggleLoginWithPassword() { _loginWithPasswordMode.value = !_loginWithPasswordMode.value }
    fun dismissSetPasswordDialog() { _showSetPasswordDialog.value = false }

    /**
     * Вход по e-mail и паролю.
     */
    fun signInWithEmail(onSuccess: (String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }
        _isLoading.value = true
        auth.signInWithEmailAndPassword(_email.value, _password.value)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val email = user?.email ?: _email.value
                    val uid = user?.uid ?: ""
                    _currentUserEmail.value = email
                    _currentUid.value = uid
                    
                    // Синхронизация профиля в Firestore и RTDB для видимости в чате
                    val profile = UserProfile(uid, email, email.substringBefore("@"))
                    firestore.collection("users").document(uid).set(profile, com.google.firebase.firestore.SetOptions.merge())
                    database.getReference("users").child(uid).updateChildren(mapOf(
                        "uid" to uid,
                        "email" to email,
                        "name" to email.substringBefore("@")
                    ))
                    
                    fetchUserProfile(uid)
                    _isLoading.value = false
                    onSuccess(email)
                } else {
                    _isLoading.value = false
                    _error.value = when {
                        task.exception is FirebaseAuthInvalidUserException -> "Пользователь не найден"
                        task.exception is FirebaseAuthInvalidCredentialsException -> "Неверный пароль"
                        else -> task.exception?.message
                    }
                }
            }
    }

    /**
     * Регистрация нового аккаунта по e-mail.
     */
    fun signUpWithEmail(onSuccess: (String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank() || _confirmPassword.value.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }
        if (_password.value != _confirmPassword.value) {
            _error.value = "Пароли не совпадают"
            return
        }
        if (_password.value.length < 6) {
            _error.value = "Пароль должен быть не менее 6 символов"
            return
        }
        _isLoading.value = true
        auth.createUserWithEmailAndPassword(_email.value, _password.value)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val profile = UserProfile(user?.uid ?: "", _email.value, _email.value.substringBefore("@"))
                    // Сохранение в обе базы данных
                    database.getReference("users").child(profile.uid).setValue(profile)
                    firestore.collection("users").document(profile.uid).set(profile)
                    val email = user?.email ?: _email.value
                    _currentUserEmail.value = email
                    _password.value = "" 
                    _confirmPassword.value = ""
                    onSuccess(email)
                } else {
                    _error.value = task.exception?.message
                }
            }
    }

    /**
     * Вход по номеру телефона и паролю (для пользователей, уже установивших пароль).
     */
    fun signInWithPhoneAndPassword(onSuccess: (String) -> Unit) {
        if (_phoneNumber.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Заполните все поля"
            return
        }
        _isLoading.value = true
        val dummyEmail = "${_phoneNumber.value.replace("+", "")}@phone.gym"
        auth.signInWithEmailAndPassword(dummyEmail, _password.value)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val phone = _phoneNumber.value
                    _currentUserEmail.value = phone
                    _currentUid.value = user?.uid ?: ""
                    fetchUserProfile(user?.uid ?: "")
                    onSuccess(phone)
                } else {
                    _error.value = "Неверный логин или пароль"
                }
            }
    }

    /**
     * Начало верификации по номеру телефона (отправка SMS).
     */
    fun startPhoneVerification(activity: Activity) {
        if (_phoneNumber.value.isBlank() || !_phoneNumber.value.startsWith("+")) {
            _error.value = "Введите номер в формате +7..."
            return
        }

        _isLoading.value = true
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(_phoneNumber.value.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Автоматический вход, если Firebase может прочитать SMS
                    signInWithPhoneCredential(credential, onSuccess = { email -> 
                        _currentUserEmail.value = email
                    })
                }
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    _isLoading.value = false
                    _error.value = "Ошибка проверки телефона"
                    android.util.Log.e("AuthViewModel", "Phone verification failed", e)
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    _isLoading.value = false
                    _verificationId.value = id
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Подтверждение кода из SMS.
     */
    fun verifyOtp(context: Context, onSuccess: (String) -> Unit) {
        if (_otpCode.value.isBlank()) {
            _error.value = "Введите код подтверждения"
            return
        }

        val credential = PhoneAuthProvider.getCredential(_verificationId.value!!, _otpCode.value)
        signInWithPhoneCredential(credential) { emailOrPhone ->
            val uid = auth.currentUser?.uid ?: ""
            database.getReference("users").child(uid).get().addOnSuccessListener { snapshot ->
                val profile = snapshot.getValue(UserProfile::class.java)
                // Если пользователь зашел по SMS впервые, просим установить пароль
                if (profile == null || !profile.hasPassword) {
                    _showSetPasswordDialog.value = true
                }
                onSuccess(emailOrPhone)
            }
        }
    }

    /**
     * Вход через Firebase Credential (телефон/Google).
     */
    private fun signInWithPhoneCredential(credential: PhoneAuthCredential, onSuccess: (String) -> Unit) {
        _isLoading.value = true
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            _isLoading.value = false
            if (task.isSuccessful) {
                val user = auth.currentUser
                val phone = user?.phoneNumber ?: _phoneNumber.value
                val uid = user?.uid ?: ""
                
                firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                    val existingProfile = snapshot.toObject(UserProfile::class.java)
                    if (existingProfile == null) {
                        // Создаем технический e-mail для мобильного входа
                        val dummyEmail = "${phone.replace("+", "")}@phone.gym"
                        val profile = UserProfile(uid, dummyEmail, "Пользователь", hasPassword = false)
                        database.getReference("users").child(uid).setValue(profile)
                        firestore.collection("users").document(uid).set(profile)
                        _currentUserProfile.value = profile
                    } else {
                        _currentUserProfile.value = existingProfile
                    }
                    _currentUserEmail.value = phone
                    onSuccess(phone)
                }
            } else {
                _error.value = task.exception?.message
            }
        }
    }

    /**
     * Установка пароля для пользователя, зашедшего по номеру телефона.
     */
    fun setPasswordForPhoneUser(password: String, onSuccess: () -> Unit) {
        val user = auth.currentUser ?: return
        val phone = user.phoneNumber ?: return
        val dummyEmail = "${phone.replace("+", "")}@phone.gym"
        
        _isLoading.value = true
        val credential = EmailAuthProvider.getCredential(dummyEmail, password)
        
        user.linkWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                database.getReference("users").child(user.uid).child("hasPassword").setValue(true)
                firestore.collection("users").document(user.uid).update("hasPassword", true)
                _currentUserProfile.value = _currentUserProfile.value?.copy(hasPassword = true)
                _showSetPasswordDialog.value = false
                _isLoading.value = false
                onSuccess()
            } else {
                _isLoading.value = false
                _error.value = "Не удалось установить пароль"
            }
        }
    }

    /**
     * Вход через Google.
     */
    fun signInWithGoogle(idToken: String, onSuccess: (String) -> Unit) {
        _isLoading.value = true
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val email = user?.email ?: ""
                val uid = user?.uid ?: ""
                
                firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                    val existingProfile = snapshot.toObject(UserProfile::class.java)
                    if (existingProfile == null) {
                        val profile = UserProfile(uid, email, user?.displayName ?: email.substringBefore("@"))
                        database.getReference("users").child(uid).setValue(profile)
                        firestore.collection("users").document(uid).set(profile)
                        _currentUserProfile.value = profile
                    } else {
                        _currentUserProfile.value = existingProfile
                    }
                    _currentUserEmail.value = email
                    _isLoading.value = false
                    onSuccess(email)
                }
            } else {
                _isLoading.value = false
                _error.value = task.exception?.message
            }
        }
    }

    /**
     * Выход из аккаунта.
     */
    fun signOut() {
        auth.signOut()
        _currentUserEmail.value = null
    }
}
