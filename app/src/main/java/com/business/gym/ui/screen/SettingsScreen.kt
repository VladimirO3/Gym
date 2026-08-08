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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.SettingsViewModel
import com.business.gym.ui.viewmodel.AuthViewModel

import androidx.compose.ui.graphics.Color

@Composable
fun SettingsScreen(
    currentUserEmail: String?,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val contentModifier = if (isWideScreen) Modifier.width(600.dp) else Modifier.fillMaxWidth()
    
    val themeMode by viewModel.themeMode
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }
    val serverIp by viewModel.serverIp
    var serverIpInput by remember { mutableStateOf(serverIp) }
    
    // Синхронизируем ввод, когда меняется значение в ViewModel (например, при загрузке)
    LaunchedEffect(serverIp) {
        serverIpInput = serverIp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Red,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = contentModifier,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.settings_theme_mode), 
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(modifier = contentModifier) {
            ThemeOption("light", themeMode, stringResource(R.string.theme_light)) { viewModel.setThemeMode(context, currentUserEmail, it) }
            ThemeOption("dark", themeMode, stringResource(R.string.theme_dark)) { viewModel.setThemeMode(context, currentUserEmail, it) }
            ThemeOption("system", themeMode, stringResource(R.string.theme_system)) { viewModel.setThemeMode(context, currentUserEmail, it) }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.settings_language), 
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        val currentLocale = if (AppCompatDelegate.getApplicationLocales().isEmpty) "system" 
                           else AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "system"
        
        Column(modifier = contentModifier) {
            LanguageOption("system", currentLocale, stringResource(R.string.language_system)) { viewModel.setLanguage(context, currentUserEmail, it) }
            LanguageOption("en", currentLocale, stringResource(R.string.language_english)) { viewModel.setLanguage(context, currentUserEmail, it) }
            LanguageOption("ru", currentLocale, stringResource(R.string.language_russian)) { viewModel.setLanguage(context, currentUserEmail, it) }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Настройки подключения", 
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = serverIpInput,
            onValueChange = { serverIpInput = it },
            label = { Text("IP-адрес сервера:Порт") },
            modifier = contentModifier,
            placeholder = { Text("10.0.2.2:5557") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Red,
                focusedLabelColor = Color.Red
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { 
                viewModel.setServerIp(context, currentUserEmail, serverIpInput)
                android.widget.Toast.makeText(context, "IP-адрес обновлен", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = contentModifier,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            Text("Сохранить IP", color = Color.White)
        }

        if (isAdmin) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Pending Approvals (Admin)", 
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
                modifier = contentModifier
            )
            
            val pendingUsers by authViewModel.pendingUsers
            LaunchedEffect(Unit) {
                authViewModel.fetchPendingUsers()
            }
            // ...

            if (pendingUsers.isEmpty()) {
                Text(
                    "No pending registration requests", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = contentModifier.padding(vertical = 8.dp)
                )
            } else {
                pendingUsers.forEach { user ->
                    Card(
                        modifier = contentModifier.padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
                                Text(user.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Button(
                                onClick = { authViewModel.approveUser(user.uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Approve", color = Color.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (currentUserEmail != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Logged in as: $currentUserEmail", 
                style = MaterialTheme.typography.bodyMedium, 
                color = Color.Gray,
                modifier = contentModifier
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLogout,
                modifier = contentModifier.height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.auth_logout), color = Color.White)
            }
        }
    }
}

@Composable
fun ThemeOption(mode: String, currentMode: String, label: String, onClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().clickable { onClick(mode) }.padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = currentMode == mode, 
            onClick = { onClick(mode) },
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Red, 
                unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun LanguageOption(lang: String, currentLang: String, label: String, onClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().clickable { onClick(lang) }.padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = currentLang == lang, 
            onClick = { onClick(lang) },
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Red, 
                unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}
