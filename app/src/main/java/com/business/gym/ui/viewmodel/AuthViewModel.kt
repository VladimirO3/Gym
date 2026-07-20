package com.business.gym.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.business.gym.data.model.UserProfile
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

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

    private val _verificationId = mutableStateOf<String?>(null)
    val verificationId: State<String?> = _verificationId

    private val _authMode = mutableStateOf("email") // "email", "phone"
    val authMode: State<String> = _authMode

    private val _isLogin = mutableStateOf(true)
    val isLogin: State<Boolean> = _isLogin

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _currentUserProfile = mutableStateOf<UserProfile?>(null)
    val currentUserProfile: State<UserProfile?> = _currentUserProfile

    private val _currentUserEmail = mutableStateOf(auth.currentUser?.email ?: auth.currentUser?.phoneNumber)
    val currentUserEmail: State<String?> = _currentUserEmail

    private val _showSetPasswordDialog = mutableStateOf(false)
    val showSetPasswordDialog: State<Boolean> = _showSetPasswordDialog

    private val _loginWithPasswordMode = mutableStateOf(false)
    val loginWithPasswordMode: State<Boolean> = _loginWithPasswordMode

    init {
        auth.currentUser?.let { fetchUserProfile(it.uid) }
    }

    private fun fetchUserProfile(uid: String) {
        database.getReference("users").child(uid).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(UserProfile::class.java)
            _currentUserProfile.value = profile
            if (profile != null && profile.email.endsWith("@phone.gym") && !profile.hasPassword) {
                // Potential to show set password dialog if needed
            }
        }
    }

    // Admin Email
    companion object {
        const val ADMIN_EMAIL = "verso0100@gmail.com"
    }

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

    fun signInWithEmail(onSuccess: (String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Fields cannot be empty"
            return
        }
        _isLoading.value = true
        auth.signInWithEmailAndPassword(_email.value, _password.value)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val email = user?.email ?: _email.value
                    _currentUserEmail.value = email
                    _password.value = "" // Clear password from memory
                    onSuccess(email)
                } else {
                    _error.value = task.exception?.message
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
                    database.getReference("users").child(profile.uid).setValue(profile)
                    val email = user?.email ?: _email.value
                    _currentUserEmail.value = email
                    _password.value = "" // Clear password from memory
                    _confirmPassword.value = ""
                    onSuccess(email)
                } else {
                    _error.value = task.exception?.message
                }
            }
    }

    fun startPhoneVerification(activity: Activity) {
        if (_phoneNumber.value.isBlank() || !_phoneNumber.value.startsWith("+")) {
            _error.value = "Enter phone number starting with + (e.g. +7...)"
            return
        }

        _isLoading.value = true
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(_phoneNumber.value.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneCredential(credential, onSuccess = { email -> 
                        _currentUserEmail.value = email
                    })
                }
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    _isLoading.value = false
                    _error.value = when {
                        e.message?.contains("reCAPTCHA") == true -> "Ошибка проверки (reCAPTCHA). Попробуйте позже."
                        e.message?.contains("quota") == true -> "Лимит SMS исчерпан. Попробуйте другой способ."
                        e.message?.contains("not allowed") == true || e.message?.contains("region enabled") == true -> 
                            "Вход по телефону не включен или регион заблокирован в Firebase Console."
                        else -> "Ошибка: ${e.localizedMessage}"
                    }
                    android.util.Log.e("AuthViewModel", "Phone verification failed", e)
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    _isLoading.value = false
                    _verificationId.value = id
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(context: Context, onSuccess: (String) -> Unit) {
        if (_otpCode.value.isBlank()) {
            _error.value = "Enter verification code"
            return
        }

        val credential = PhoneAuthProvider.getCredential(_verificationId.value!!, _otpCode.value)
        signInWithPhoneCredential(credential) { emailOrPhone ->
            // After successful SMS login, check if user needs to set a password
            val uid = auth.currentUser?.uid ?: ""
            database.getReference("users").child(uid).get().addOnSuccessListener { snapshot ->
                val profile = snapshot.getValue(UserProfile::class.java)
                if (profile == null || !profile.hasPassword) {
                    _showSetPasswordDialog.value = true
                }
                onSuccess(emailOrPhone)
            }
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential, onSuccess: (String) -> Unit) {
        _isLoading.value = true
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            _isLoading.value = false
            if (task.isSuccessful) {
                val user = auth.currentUser
                val phone = user?.phoneNumber ?: _phoneNumber.value
                val uid = user?.uid ?: ""
                
                // Fetch or create profile
                database.getReference("users").child(uid).get().addOnSuccessListener { snapshot ->
                    val existingProfile = snapshot.getValue(UserProfile::class.java)
                    if (existingProfile == null) {
                        val dummyEmail = "${phone.replace("+", "")}@phone.gym"
                        val profile = UserProfile(uid, dummyEmail, "User", hasPassword = false)
                        database.getReference("users").child(uid).setValue(profile)
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

    fun setPasswordForPhoneUser(password: String, onSuccess: () -> Unit) {
        val user = auth.currentUser ?: return
        val phone = user.phoneNumber ?: return
        val dummyEmail = "${phone.replace("+", "")}@phone.gym"
        
        _isLoading.value = true
        // To set a password, we link with EmailAuthProvider
        val credential = EmailAuthProvider.getCredential(dummyEmail, password)
        
        user.linkWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                database.getReference("users").child(user.uid).child("hasPassword").setValue(true)
                _currentUserProfile.value = _currentUserProfile.value?.copy(hasPassword = true)
                _showSetPasswordDialog.value = false
                _isLoading.value = false
                onSuccess()
            } else {
                _isLoading.value = false
                _error.value = "Failed to set password: ${task.exception?.message}"
            }
        }
    }

    fun signInWithPhoneAndPassword(onSuccess: (String) -> Unit) {
        if (_phoneNumber.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Enter phone and password"
            return
        }
        val dummyEmail = "${_phoneNumber.value.replace("+", "").replace(" ", "")}@phone.gym"
        _isLoading.value = true
        auth.signInWithEmailAndPassword(dummyEmail, _password.value).addOnCompleteListener { task ->
            _isLoading.value = false
            if (task.isSuccessful) {
                val user = auth.currentUser
                val phone = user?.phoneNumber ?: _phoneNumber.value
                _currentUserEmail.value = phone
                fetchUserProfile(user?.uid ?: "")
                onSuccess(phone)
            } else {
                _error.value = "Invalid phone or password"
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: (String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        _isLoading.value = true
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            _isLoading.value = false
            if (task.isSuccessful) {
                val user = auth.currentUser
                val email = user?.email ?: user?.phoneNumber ?: "Google User"
                val profile = UserProfile(
                    uid = user?.uid ?: "",
                    email = email,
                    name = user?.displayName ?: email.substringBefore("@")
                )
                database.getReference("users").child(profile.uid).setValue(profile)
                _currentUserEmail.value = email
                onSuccess(email)
            } else {
                _error.value = "Google Sign-In failed: ${task.exception?.message}"
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _currentUserEmail.value = null
    }
}
