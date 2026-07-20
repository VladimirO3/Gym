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

    private val _currentUserEmail = mutableStateOf(auth.currentUser?.email ?: auth.currentUser?.phoneNumber)
    val currentUserEmail: State<String?> = _currentUserEmail

    // Admin Phone Number - moved to constant for now, but should be in Firebase Config or Database
    companion object {
        const val ADMIN_PHONE = "+79530481451"
    }

    fun onEmailChange(newValue: String) { _email.value = newValue; _error.value = null }
    fun onPasswordChange(newValue: String) { _password.value = newValue; _error.value = null }
    fun onPhoneNumberChange(newValue: String) { _phoneNumber.value = newValue; _error.value = null }
    fun onOtpCodeChange(newValue: String) { _otpCode.value = newValue; _error.value = null }
    fun setAuthMode(mode: String) { _authMode.value = mode; _error.value = null }
    fun toggleIsLogin() { _isLogin.value = !_isLogin.value }

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
                    onSuccess(email)
                } else {
                    _error.value = task.exception?.message
                }
            }
    }

    fun signUpWithEmail(onSuccess: (String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _error.value = "Fields cannot be empty"
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
        
        // Special case for Admin: bypass real SMS sending
        if (_phoneNumber.value.trim() == ADMIN_PHONE) {
            _verificationId.value = "admin_bypass"
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
                    _error.value = e.message
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

        // Special check for Admin "Backdoor"
        if (_phoneNumber.value.trim() == ADMIN_PHONE && _otpCode.value == "qwerty") {
            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("user_session_hash", "admin_session")
                .putString("user_session_email", _phoneNumber.value)
                .putLong("user_session_timestamp", System.currentTimeMillis())
                .apply()
            
            _currentUserEmail.value = _phoneNumber.value
            onSuccess(_phoneNumber.value)
            return
        }

        if (_verificationId.value == "admin_bypass") {
            _error.value = "Invalid admin code"
            return
        }

        val credential = PhoneAuthProvider.getCredential(_verificationId.value!!, _otpCode.value)
        signInWithPhoneCredential(credential, onSuccess)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential, onSuccess: (String) -> Unit) {
        _isLoading.value = true
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            _isLoading.value = false
            if (task.isSuccessful) {
                val user = auth.currentUser
                val phone = user?.phoneNumber ?: _phoneNumber.value
                val profile = UserProfile(user?.uid ?: "", phone, "User")
                database.getReference("users").child(profile.uid).setValue(profile)
                _currentUserEmail.value = phone
                onSuccess(phone)
            } else {
                _error.value = task.exception?.message
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

    fun signOut(context: Context) {
        auth.signOut()
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("user_session_hash")
            .remove("user_session_email")
            .remove("user_session_timestamp")
            .apply()
        _currentUserEmail.value = null
    }
}
