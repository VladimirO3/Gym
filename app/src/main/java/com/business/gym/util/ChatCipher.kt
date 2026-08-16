package com.business.gym.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChatCipher(secret: String) {
    private val keyBytes: ByteArray = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        return try {
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // Format: enc:v1:ivBase64:encryptedBase64
            "enc:v1:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            android.util.Log.e("ChatCipher", "Encryption error", e)
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        if (!cipherText.startsWith("enc:v1:")) return cipherText
        return try {
            val parts = cipherText.split(":")
            if (parts.size < 4) return cipherText
            
            val iv = Base64.decode(parts[2], Base64.DEFAULT)
            val encrypted = Base64.decode(parts[3], Base64.DEFAULT)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("ChatCipher", "Decryption error", e)
            cipherText
        }
    }
}
