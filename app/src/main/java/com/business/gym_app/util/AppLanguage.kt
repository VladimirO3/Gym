package com.business.gym_app.util

import android.content.Context
import java.util.Locale

object AppLanguage {
    fun current(context: Context): String {
        val saved = context.getSharedPreferences("settings_global", Context.MODE_PRIVATE)
            .getString("lang", "system")
            ?.lowercase()
            ?.substringBefore('-')
        return if (saved == "en" || saved == "ru") saved else Locale.getDefault().language
            .lowercase()
            .substringBefore('-')
            .let { if (it == "en") "en" else "ru" }
    }
}
