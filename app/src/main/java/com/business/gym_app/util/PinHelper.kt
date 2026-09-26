package com.business.gym_app.util

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Локальный PIN-код из 4 цифр для быстрого входа.
 *
 * Хранится только SHA-256 хеш (PIN + случайная соль) в приватных
 * SharedPreferences — сам PIN нигде не сохраняется. PIN привязан
 * к сохранённому логину (email/телефон): при смене аккаунта старый
 * PIN не подходит и создаётся заново.
 */
object PinHelper {
    private const val PREFS_NAME = "pin_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ACCOUNT = "pin_account"
    private const val KEY_PIN_CREATED_AT = "pin_created_at"

    const val PIN_LENGTH = 4

    /** PIN нужно менять каждые 7 дней. */
    const val PIN_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

    fun isPinSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
    }

    fun pinAccount(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN_ACCOUNT, null)
    }

    /** PIN принадлежит текущему сохранённому логину? */
    fun isPinForAccount(context: Context, account: String): Boolean {
        if (!isPinSet(context)) return false
        val saved = pinAccount(context).orEmpty()
        if (saved.isBlank()) return false
        return saved.trim().lowercase() == account.trim().lowercase()
    }

    fun isValidPinFormat(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    fun setPin(context: Context, account: String, pin: String): Boolean {
        if (!isValidPinFormat(pin) || account.isBlank()) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt) ?: return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_ACCOUNT, account.trim())
            .putLong(KEY_PIN_CREATED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    fun verifyPin(context: Context, account: String, pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedHashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val savedAccount = prefs.getString(KEY_PIN_ACCOUNT, null).orEmpty()
        if (savedAccount.isBlank() || savedAccount.trim().lowercase() != account.trim().lowercase()) {
            return false
        }
        return try {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(savedHashB64, Base64.NO_WRAP)
            val actual = hashPin(pin, salt) ?: return false
            MessageDigest.isEqual(expected, actual)
        } catch (e: Exception) {
            false
        }
    }

    fun clearPin(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Когда PIN был создан/обновлён (мс, System.currentTimeMillis), 0 если неизвестно. */
    fun pinCreatedAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_PIN_CREATED_AT, 0L)
    }

    /** Сколько полных дней осталось до обязательной смены PIN (0 если срок вышел). */
    fun pinDaysLeft(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val created = pinCreatedAt(context)
        if (created <= 0L) return 0L
        val left = PIN_MAX_AGE_MS - (nowMs - created)
        if (left <= 0L) return 0L
        // Округляем вверх: даже остаток в 1 час = "остался 1 день".
        return (left + 24L * 60L * 60L * 1000L - 1L) / (24L * 60L * 60L * 1000L)
    }

    /** Срок PIN вышел (прошло 7+ дней с создания)? */
    fun isPinExpired(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isPinSet(context)) return false
        val created = pinCreatedAt(context)
        // Старые PIN без метки времени считаем просроченными — попросим обновить.
        if (created <= 0L) return true
        return nowMs - created >= PIN_MAX_AGE_MS
    }

    /** Скоро истекает (осталось <= 2 дней)? Для мягкого напоминания. */
    fun isPinExpiringSoon(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isPinSet(context) || isPinExpired(context, nowMs)) return false
        return pinDaysLeft(context, nowMs) <= 2L
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.digest(pin.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
