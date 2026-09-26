package com.business.gym_app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Периодический запрос отзыва: каждые [FEEDBACK_INTERVAL_DAYS] дней.
 *
 * Храним дату последнего показанного окна в приватных SharedPreferences
 * (привязана к логину). Окно можно закрыть крестиком — счётчик всё равно
 * обнуляется, чтобы не мучить пользователя повторно на каждом запуске.
 */
object FeedbackHelper {
    private const val PREFS_NAME = "feedback_prefs"
    private const val KEY_LAST_SHOWN_AT = "feedback_last_shown_at"
    private const val KEY_ACCOUNT = "feedback_account"

    /** Отзыв просим раз в 10 дней. */
    const val FEEDBACK_INTERVAL_DAYS = 10L

    const val FEEDBACK_INTERVAL_MS = FEEDBACK_INTERVAL_DAYS * 24L * 60L * 60L * 1000L

    /** Почта разработчика, куда уходят отзывы и предложения. */
    const val DEVELOPER_EMAIL = "veerso0100@gmail.com"

    /** Тема письма — по ней легко находить отзывы в почтовом ящике. */
    const val SUBJECT = "Отзыв о приложении GYM ABS"

    /**
     * Нужно ли показывать окно отзыва.
     *
     * Гостю и пользователю без логина окно не показываем — отзыв должен быть
     * привязан к аккаунту. Метки нет (первый запуск) — показываем сразу.
     */
    fun shouldAskForFeedback(
        context: Context,
        account: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (account.isNullOrBlank()) return false
        val tracked = trackedAccount(context).orEmpty()
        // Метка от другого аккаунта — считаем, что для него окно ещё не показывалось.
        if (tracked.isNotBlank() &&
            tracked.trim().lowercase() != account.trim().lowercase()
        ) {
            return true
        }
        val lastShown = lastShownAt(context)
        if (lastShown <= 0L) return true
        return nowMs - lastShown >= FEEDBACK_INTERVAL_MS
    }

    /** Дата последнего показа окна (мс), 0 если ещё не показывали. */
    fun lastShownAt(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SHOWN_AT, 0L)

    /** Логин, для которого считается счётчик. */
    fun trackedAccount(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT, null)

    /** Сколько дней прошло с прошлого окна отзыва (0, если его не было). */
    fun daysSinceLastShown(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastShownAt(context)
        if (last <= 0L) return 0L
        val elapsed = nowMs - last
        if (elapsed <= 0L) return 0L
        return elapsed / (24L * 60L * 60L * 1000L)
    }

    /** Сколько дней осталось до следующего окна отзыва (0 — пора показывать). */
    fun daysUntilNextRequest(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastShownAt(context)
        if (last <= 0L) return 0L
        val left = FEEDBACK_INTERVAL_MS - (nowMs - last)
        if (left <= 0L) return 0L
        val dayMs = 24L * 60L * 60L * 1000L
        return (left + dayMs - 1L) / dayMs
    }

    /**
     * Отметить, что окно показано (пользователь отправил отзыв или закрыл крестиком).
     * Следующий запрос — через [FEEDBACK_INTERVAL_DAYS] дней.
     */
    fun markShown(context: Context, account: String? = null, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_LAST_SHOWN_AT, nowMs)
            if (account.isNullOrBlank()) remove(KEY_ACCOUNT) else putString(KEY_ACCOUNT, account.trim())
            apply()
        }
    }

    /** Сбросить счётчик (например, при выходе из аккаунта). */
    fun clearTracking(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Текст письма: оценка звёздами, комментарий и версия приложения.
     * Отдельная чистая функция — её удобно проверять в тестах.
     */
    fun buildFeedbackBody(
        rating: Int,
        comment: String,
        account: String?,
        appVersion: String
    ): String {
        val stars = buildString {
            for (i in 1..5) append(if (i <= rating) "★" else "☆")
        }
        return buildString {
            append("Оценка: $rating/5  $stars\n\n")
            if (comment.isNotBlank()) {
                append("Отзыв и предложения:\n")
                append(comment.trim())
                append("\n\n")
            } else {
                append("Отзыв и предложения: (не указаны)\n\n")
            }
            append("Пользователь: ${account.orEmpty().ifBlank { "неизвестно" }}\n")
            append("Версия приложения: $appVersion\n")
            append("Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())}")
        }
    }

    /**
     * Intent, открывающий почтовое приложение с готовым письмом
     * на [DEVELOPER_EMAIL]. Отправка идёт через системный почтовый клиент:
     * приложению не нужен свой SMTP-сервер с паролем.
     */
    fun buildEmailIntent(
        rating: Int,
        comment: String,
        account: String?,
        appVersion: String
    ): Intent {
        val body = buildFeedbackBody(rating, comment, account, appVersion)
        val uri = Uri.parse("mailto:$DEVELOPER_EMAIL").buildUpon()
            .appendQueryParameter("subject", SUBJECT)
            .appendQueryParameter("body", body)
            .build()
        return Intent(Intent.ACTION_SENDTO).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
