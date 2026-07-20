package com.business.gym.ui.screen

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    currentUserEmail: String?,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode
    
    LaunchedEffect(currentUserEmail) {
        viewModel.loadSettings(context, currentUserEmail)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Column {
            ThemeOption("light", themeMode, stringResource(R.string.theme_light)) { viewModel.setThemeMode(context, currentUserEmail, it) }
            ThemeOption("dark", themeMode, stringResource(R.string.theme_dark)) { viewModel.setThemeMode(context, currentUserEmail, it) }
            ThemeOption("system", themeMode, stringResource(R.string.theme_system)) { viewModel.setThemeMode(context, currentUserEmail, it) }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        val currentLocale = if (AppCompatDelegate.getApplicationLocales().isEmpty) "system" 
                           else AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "system"
        
        Column {
            LanguageOption("system", currentLocale, stringResource(R.string.language_system)) { viewModel.setLanguage(context, currentUserEmail, it) }
            LanguageOption("en", currentLocale, stringResource(R.string.language_english)) { viewModel.setLanguage(context, currentUserEmail, it) }
            LanguageOption("ru", currentLocale, stringResource(R.string.language_russian)) { viewModel.setLanguage(context, currentUserEmail, it) }
        }

        if (currentUserEmail != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Logged in as: $currentUserEmail", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.auth_logout))
            }
        }
    }
}

@Composable
fun ThemeOption(mode: String, currentMode: String, label: String, onClick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onClick(mode) }) {
        RadioButton(selected = currentMode == mode, onClick = { onClick(mode) })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LanguageOption(lang: String, currentLang: String, label: String, onClick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onClick(lang) }) {
        RadioButton(selected = currentLang == lang, onClick = { onClick(lang) })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
