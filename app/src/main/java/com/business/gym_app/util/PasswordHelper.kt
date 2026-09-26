package com.business.gym_app.util

import android.content.Context

/**
 * Обязательная смена пароля каждые [PASSWORD_MAX_AGE_DAYS] дней.
 *
 * Храним только дату последней смены пароля в приватных SharedPreferences
 * (привязана к логину). Если метки нет — считаем, что срок уже вышел,
 * и просим пользователя сменить пароль при первом входе.
 *
 * Счётчик обнуляется при успешной смене пароля ([markPasswordChanged]),
 * поэтому дата всегда соответствует последнему установленному паролю.
 */
object PasswordHelper {
    private const val PREFS_NAME = "password_prefs"
    private const val KEY_CHANGED_AT = "password_changed_at"
    private const val KEY_ACCOUNT = "password_account"

    /** Пароль нужно менять каждые 21 день. */
    const val PASSWORD_MAX_AGE_DAYS = 21L

    const val PASSWORD_MAX_AGE_MS = PASSWORD_MAX_AGE_DAYS * 24L * 60L * 60L * 1000L

    /** Сколько дней осталось до обязательной смены (0, если срок вышел или метки нет). */
    fun passwordDaysLeft(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val changed = passwordChangedAt(context)
        if (changed <= 0L) return 0L
        val left = PASSWORD_MAX_AGE_MS - (nowMs - changed)
        if (left <= 0L) return 0L
        // Округляем вверх: остаток меньше суток = "остался ещё 1 день".
        val dayMs = 24L * 60L * 60L * 1000L
        return (left + dayMs - 1L) / dayMs
    }

    /** Дата последней смены пароля (мс), 0 если неизвестна. */
    fun passwordChangedAt(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CHANGED_AT, 0L)

    /** К какому логину относится отслеживаемый срок. */
    fun trackedAccount(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT, null)

    /** Пароль просрочен и подлежит обязательной смене. */
    fun isPasswordExpired(
        context: Context,
        account: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val changed = passwordChangedAt(context)
        // Метки нет (первый вход после обновления) — сразу просим сменить пароль.
        if (changed <= 0L) return true
        // Метка относится к другому аккаунту — тоже считаем просроченной.
        val tracked = trackedAccount(context).orEmpty()
        if (!account.isNullOrBlank() && !tracked.isBlank() &&
            tracked.trim().lowercase() != account.trim().lowercase()
        ) {
            return true
        }
        return nowMs - changed >= PASSWORD_MAX_AGE_MS
    }

    /** Отметить успешную смену пароля — 21-дневный срок отсчитывается заново. */
    fun markPasswordChanged(context: Context, account: String? = null, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_CHANGED_AT, nowMs)
            // Всегда синхронизируем учётный логин: пустое значение сбрасывает старую
            // метку, иначе после смены пароля счётчик считался бы просроченным.
            if (account.isNullOrBlank()) remove(KEY_ACCOUNT) else putString(KEY_ACCOUNT, account.trim())
            apply()
        }
    }

    /** Сколько дней прошло с последней смены пароля (0, если метки нет). */
    fun daysSinceChange(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val changed = passwordChangedAt(context)
        if (changed <= 0L) return 0L
        val elapsed = nowMs - changed
        if (elapsed <= 0L) return 0L
        val dayMs = 24L * 60L * 60L * 1000L
        return elapsed / dayMs
    }

    /** Сбросить счётчик (например, при выходе из аккаунта). */
    fun clearPasswordTracking(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}