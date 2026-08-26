package com.business.gym.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.business.gym.R
import com.business.gym.data.model.NewsItem
import com.business.gym.data.api.NewsApiService
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
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    // В альбомной ориентации или на планшетах тоже по 1 колонке для лучшего фокуса на контенте,
    // но с ограничением максимальной ширины
    val columns = 1
    
    val jwtToken by authViewModel.jwtToken
    val isGuest by authViewModel.isGuest

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
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    
    var selectedNewsForFullScreen by remember { mutableStateOf<NewsItem?>(null) }

    if (selectedNewsForFullScreen != null) {
        val news = selectedNewsForFullScreen!!
        val isTextOnly = news.url.isBlank() || news.url.endsWith("/uploads/") || news.url == "/uploads"
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedNewsForFullScreen = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Затемнение фона (Scrim)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { selectedNewsForFullScreen = null }
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f)
                        .then(if (isWideScreen) Modifier.width(600.dp) else Modifier),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A1A), // Темно-серый фон карточки
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (!isTextOnly) {
                                if (news.type == "video") {
                                    VideoPlayer(
                                        videoUrl = news.url,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f),
                                        autoPlay = true
                                    )
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(news.url)
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                            
                            // Кнопка закрытия
                            IconButton(
                                onClick = { selectedNewsForFullScreen = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Column(modifier = Modifier.padding(20.dp)) {
                            if (!news.title.isNullOrBlank()) {
                                Text(
                                    text = news.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (!news.content.isNullOrBlank()) {
                                Text(
                                    text = news.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    lineHeight = 22.sp
                                )
                            }
                            
                            val dateText = remember(news.timestamp) {
                                if (news.timestamp > 0) {
                                    val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
                                    sdf.format(Date(news.timestamp))
                                } else ""
                            }
                            
                            if (dateText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun createTempFile(extension: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(if (extension == "jpg") android.os.Environment.DIRECTORY_PICTURES else android.os.Environment.DIRECTORY_MOVIES)
        return File.createTempFile("NEWS_${timeStamp}_", ".$extension", storageDir)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: ""
            if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                selectedMediaUri = it
            } else {
                Toast.makeText(context, "Это не фото или видео файл!", Toast.LENGTH_LONG).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedMediaUri = tempUri
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            selectedMediaUri = tempUri
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Разрешение на камеру отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    if (showLocalAddDialog) {
        AlertDialog(
            onDismissRequest = { showLocalAddDialog = false },
            containerColor = Color.Black,
            title = { Text(stringResource(R.string.news_add_local), color = Color.Red) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = localTitle,
                        onValueChange = { localTitle = it },
                        label = { Text(stringResource(R.string.news_title_label), color = Color.White) },
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
                        label = { Text(stringResource(R.string.news_content_label), color = Color.White) },
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
                            text = if (isVideo) stringResource(R.string.news_video_selected) else stringResource(R.string.news_photo_selected), 
                            color = Color.Green, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Button(
                        onClick = { 
                            galleryLauncher.launch("image/*,video/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedMediaUri == null) stringResource(R.string.news_from_gallery) else stringResource(R.string.news_from_gallery))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                try {
                                    val file = createTempFile("jpg")
                                    val uri = FileProvider.getUriForFile(context, "com.business.gym.fileprovider", file)
                                    tempUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка камеры: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.news_camera), fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                try {
                                    val file = createTempFile("mp4")
                                    val uri = FileProvider.getUriForFile(context, "com.business.gym.fileprovider", file)
                                    tempUri = uri
                                    videoLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка видео: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Icon(Icons.Default.Videocam, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.news_camera), fontSize = 12.sp)
                        }
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
                    else Text(stringResource(R.string.news_publish))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalAddDialog = false }) { Text(stringResource(R.string.btn_cancel), color = Color.Gray) }
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
        // ФИКСИРОВАННЫЙ ЗАГОЛОВОК (НЕ СКРОЛЛИТСЯ)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Индикатор состояния сервера
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (jwtToken != null) Color.Green else Color.Red, androidx.compose.foundation.shape.CircleShape)
            )

            // Центральный заголовок
            Text(
                text = stringResource(R.string.news_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Кнопка добавления (только для админа)
            if (isAdmin) {
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
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Content", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.news_add_local)) },
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
            } else {
                // Пустой блок для выравнивания текста по центру
                Spacer(modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .weight(1f)
                .then(if (isWideScreen) Modifier.widthIn(max = 800.dp) else Modifier.fillMaxWidth()),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ПРИОРИТЕТ: Новости с вашего VPS сервера
            if (localNews.isNotEmpty()) {
                items(localNews) { localItem ->
                    val newsItem = NewsItem(
                        id = localItem.id,
                        url = NewsApiService.getFullUrl(context, localItem.mediaUrl),
                        type = localItem.mediaType,
                        title = localItem.title,
                        content = localItem.content,
                        timestamp = localItem.createdAt.toLongOrNull() ?: 0,
                        userReaction = localItem.userReaction,
                        reactions = localItem.reactions
                    )
                    NewsMediaItem(
                        item = newsItem, 
                        isAdmin = isAdmin, 
                        isGuest = isGuest,
                        onDelete = { viewModel.deleteLocalNewsItem(localItem.id, jwtToken) },
                        onReact = { type -> viewModel.reactToNews(localItem.id, type, jwtToken) },
                        onClick = { selectedNewsForFullScreen = newsItem }
                    )
                }
            }
        }
    }
}
