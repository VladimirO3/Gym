package com.business.gym_app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Единая точка доступа к языку приложения.
 *
 * Значение хранится в SharedPreferences «settings_global» / «lang»:
 *  - «system» — язык устройства;
 *  - «en» / «ru» — явно выбранный язык.
 *
 * [AppCompatDelegate.setApplicationLocales] на Android 12 и ниже локализует только
 * AppCompatActivity, поэтому сервисы, receiver'ы, worker'ы и сам Application остаются
 * на системном языке. Для них используется [localized] / [wrap], которые применяют
 * сохраненную локаль к любому контексту (в том числе к базовому контексту Application).
 */
object AppLanguage {
    private const val PREFS = "settings_global"
    private const val KEY_LANG = "lang"

    /** Язык для запросов к серверу: «en» или «ru». */
    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "system")
            ?.lowercase()
            ?.substringBefore('-')
        return if (saved == "en" || saved == "ru") saved else Locale.getDefault().language
            .lowercase()
            .substringBefore('-')
            .let { if (it == "en") "en" else "ru" }
    }

    /** Явно выбранный пользователем язык («en»/«ru») или null, если выбран «Системный». */
    fun saved(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "system")
            ?.lowercase()
            ?.substringBefore('-')
        return if (saved == "en" || saved == "ru") saved else null
    }

    /** Активная локаль приложения: выбранная пользователем либо системная. */
    fun locale(context: Context): Locale = saved(context)?.let { Locale(it) } ?: Locale.getDefault()

    /**
     * Возвращает контекст с примененной локалью приложения.
     *
     * Используется в сервисах / уведомлениях, где ресурсы берутся из
     * applicationContext и не обновляются вместе с Activity.
     */
    fun localized(context: Context): Context {
        val locale = locale(context)
        if (context.resources.configuration.locales[0] == locale) return context
        return try {
            context.createConfigurationContext(buildConfig(context, locale))
        } catch (e: Exception) {
            context
        }
    }

    /**
     * Оборачивает базовый контекст (вызывается из `Application.attachBaseContext`),
     * чтобы весь процесс — включая фоновые компоненты — работал на выбранном языке.
     */
    fun wrap(base: Context): Context = try {
        base.createConfigurationContext(buildConfig(base, locale(base)))
    } catch (e: Exception) {
        base
    }

    private fun buildConfig(context: Context, locale: Locale): Configuration =
        Configuration(context.resources.configuration).apply {
            setLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }
}

