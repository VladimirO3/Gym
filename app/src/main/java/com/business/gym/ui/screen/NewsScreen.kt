package com.business.gym.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.business.gym.R
import com.business.gym.data.model.NewsItem
import com.business.gym.ui.component.NewsMediaItem
import com.business.gym.ui.component.VideoPlayer
import com.business.gym.ui.viewmodel.AuthViewModel
import com.business.gym.ui.viewmodel.NewsViewModel

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun NewsScreen(
    isAdmin: Boolean,
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: NewsViewModel = viewModel(
        factory = NewsViewModel.Factory(application)
    )
    val settingsViewModel: com.business.gym.ui.viewmodel.SettingsViewModel = viewModel(
        factory = com.business.gym.ui.viewmodel.SettingsViewModel.Factory(application)
    )
    
    val newsItems by viewModel.newsItems
    val localNews by viewModel.localNews
    val isUploading by viewModel.isUploading
    val serverIp by settingsViewModel.serverIp

    fun getFullUrl(rawUrl: String): String {
        if (rawUrl.isBlank() || rawUrl == "/uploads/") return ""
        if (rawUrl.startsWith("http")) return rawUrl
        val base = if (serverIp.startsWith("http")) serverIp else "http://$serverIp"
        val cleanBase = if (base.endsWith("/")) base else "$base/"
        val cleanRaw = if (rawUrl.startsWith("/")) rawUrl.substring(1) else rawUrl
        return cleanBase + cleanRaw
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val columns = if (isWideScreen) 2 else 1
    
    val jwtToken by authViewModel.jwtToken

    // Принудительно запрашиваем токен, если мы админ, но его нет
    LaunchedEffect(isAdmin, jwtToken) {
        if (isAdmin && jwtToken == null) {
            authViewModel.currentUserEmail.value?.let { email ->
                authViewModel.retryLocalLogin(email)
            }
        }
    }

    // Автоматически обновляем локальные новости, когда получен токен
    LaunchedEffect(jwtToken) {
        viewModel.fetchLocalNews(jwtToken)
    }
    
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var urlType by remember { mutableStateOf("image") }

    // Состояния для добавления новости на СВОЙ сервер
    var showLocalAddDialog by remember { mutableStateOf(false) }
    var localTitle by remember { mutableStateOf("") }
    var localContent by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (showLocalAddDialog) {
            selectedMediaUri = uri // Если открыт диалог своего сервера, просто запоминаем файл
        } else {
            uri?.let { viewModel.uploadMedia(context, it) } // Иначе сразу грузим в Firebase
        }
    }

    if (showLocalAddDialog) {
        AlertDialog(
            onDismissRequest = { showLocalAddDialog = false },
            containerColor = Color.Black,
            title = { Text("Новость на свой сервер", color = Color.Red) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = localTitle,
                        onValueChange = { localTitle = it },
                        label = { Text("Заголовок", color = Color.White) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localContent,
                        onValueChange = { localContent = it },
                        label = { Text("Текст новости", color = Color.White) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (selectedMediaUri != null) {
                        val mimeType = context.contentResolver.getType(selectedMediaUri!!) ?: ""
                        val isVideo = mimeType.contains("video")
                        Text(
                            text = if (isVideo) "Видео выбрано" else "Фото выбрано", 
                            color = Color.Green, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Button(
                        onClick = { 
                            // Правильный вызов для выбора и фото и видео
                            launcher.launch("*/*") 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedMediaUri == null) "Выбрать фото/видео" else "Изменить файл")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = jwtToken
                        if (token != null) {
                            if (localTitle.isNotBlank()) {
                                viewModel.uploadToLocalServer(
                                    context = context,
                                    uri = selectedMediaUri,
                                    title = localTitle,
                                    content = localContent,
                                    token = token,
                                    onSuccess = {
                                        showLocalAddDialog = false
                                        localTitle = ""
                                        localContent = ""
                                        selectedMediaUri = null
                                    }
                                )
                            }
                        } else {
                            Toast.makeText(context, "Ошибка: Токен авторизации не найден. Попробуйте перезайти.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isUploading && localTitle.isNotBlank(), // Кнопка активна, если введен заголовок
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    if (isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("Опубликовать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalAddDialog = false }) { Text("Отмена", color = Color.Gray) }
            }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = Color.Black,
            title = { Text(stringResource(R.string.add_by_url_title), color = Color.Red) },
            text = {
                Column(modifier = if (isWideScreen) Modifier.width(480.dp) else Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.url_label), color = Color.White) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = urlType == "image", 
                            onClick = { urlType = "image" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.Red, unselectedColor = Color.Gray)
                        )
                        Text(stringResource(R.string.url_type_image), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(
                            selected = urlType == "video", 
                            onClick = { urlType = "video" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.Red, unselectedColor = Color.Gray)
                        )
                        Text(stringResource(R.string.url_type_video), color = Color.White)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (urlInput.isNotBlank()) {
                        viewModel.addByUrl(urlInput, urlType, "", "")
                        showUrlDialog = false
                        urlInput = ""
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { 
                    Text(stringResource(R.string.btn_add), color = Color.White) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { 
                    Text(stringResource(R.string.btn_cancel), color = Color.Gray) 
                }
            }
        )
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Индикатор состояния токена для админа
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (jwtToken != null) Color.Green else Color.Red, androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (jwtToken != null) "Server Connected" else "Server Disconnected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (jwtToken != null) Color.Green else Color.Red
                    )
                }

                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { expanded = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White,
                                containerColor = Color(0xFF8B0000)
                            )
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Content")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Загрузить на свой сервер") },
                                onClick = { expanded = false; showLocalAddDialog = true },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_by_url_title)) },
                                onClick = { expanded = false; showUrlDialog = true },
                                leadingIcon = { Icon(Icons.Default.Link, null) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ПРИОРИТЕТ: Новости с вашего ТЕСТОВОГО сервера
            if (localNews.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Text(
                        "Новости с вашего сервера:", 
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(localNews) { localItem ->
                    val newsItem = NewsItem(
                        id = localItem.id,
                        url = getFullUrl(localItem.url),
                        type = localItem.type,
                        title = localItem.title,
                        content = localItem.content
                    )
                    NewsMediaItem(
                        item = newsItem, 
                        isAdmin = isAdmin, 
                        onDelete = { viewModel.deleteLocalNewsItem(localItem.id, jwtToken) }
                    )
                }
            }
            
            // ВТОРОСТЕПЕННО: Новости из Firebase (если есть)
            if (newsItems.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Text(
                        "Общие новости (Cloud):", 
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                items(newsItems) { item ->
                    NewsMediaItem(item, isAdmin, onDelete = { viewModel.deleteNewsItem(item) })
                }
            }
        }
    }
}
