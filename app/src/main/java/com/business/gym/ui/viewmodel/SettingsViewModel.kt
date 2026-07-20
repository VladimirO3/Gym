package com.business.gym.ui.viewmodel

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    private val _themeMode = mutableStateOf("system")
    val themeMode: State<String> = _themeMode

    fun loadSettings(context: Context, currentUserEmail: String?) {
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        val sharedPref = context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
        
        _themeMode.value = sharedPref.getString("theme_mode", "system") ?: "system"
        
        val savedLang = sharedPref.getString("lang", "system") ?: "system"
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        if (savedLang == "system") {
            if (!currentAppLocales.isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        } else {
            if (currentAppLocales.get(0)?.language != savedLang) {
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(savedLang)
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }

    fun setThemeMode(context: Context, currentUserEmail: String?, mode: String) {
        _themeMode.value = mode
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
    }

    fun setLanguage(context: Context, currentUserEmail: String?, lang: String) {
        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        if (lang == "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
        context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }
}
