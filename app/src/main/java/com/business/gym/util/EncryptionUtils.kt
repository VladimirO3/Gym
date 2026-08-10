package com.business.gym.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES"
    
    // В реальном приложении этот ключ должен храниться в Android Keystore
    // Или генерироваться уникально для пользователя/комнаты
    private const val KEY_STRING = "GymAppSecretKey_2026_Secure!" // Должно быть 32 символа для AES-256
    
    private val secretKey: SecretKeySpec by lazy {
        val keyBytes = KEY_STRING.take(32).toByteArray()
        SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(value: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(value.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            android.util.Log.e("EncryptionUtils", "Encryption error: ${e.message}")
            value // Возвращаем оригинал в случае ошибки (fallback)
        }
    }

    fun decrypt(value: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(value, Base64.DEFAULT)
            String(cipher.doFinal(decodedBytes))
        } catch (e: Exception) {
            // Если дешифровка не удалась, возможно сообщение не зашифровано (старые данные)
            android.util.Log.w("EncryptionUtils", "Decryption error (might be plain text): ${e.message}")
            value
        }
    }
}
