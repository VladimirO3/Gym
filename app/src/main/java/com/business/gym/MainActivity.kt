package com.business.gym

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaSession
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.business.gym.ui.component.GymBackground
import com.business.gym.ui.component.ScrollableTabRow
import com.business.gym.ui.navigation.GymNavGraph
import com.business.gym.ui.navigation.Screen
import com.business.gym.ui.screen.*
import com.business.gym.ui.theme.GymTheme
import com.business.gym.ui.viewmodel.AuthViewModel
import com.business.gym.ui.viewmodel.SettingsViewModel
import com.business.gym.ui.viewmodel.CartViewModel
import com.business.gym.ui.viewmodel.AboutViewModel
import com.business.gym.util.AuthUtils
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.net.http.HttpResponseCache.install
import android.widget.Toast
import androidx.lifecycle.lifecycleScope

import com.business.gym.data.api.NewsApiService
import kotlinx.coroutines.launch
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttp
import okhttp3.internal.http.HttpMethod
import kotlinx.coroutines.*         // launch, withContext, Dispatchers
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.business.gym.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors


class MainActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var firebaseAnalytics: FirebaseAnalytics? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Уведомления отключены", Toast.LENGTH_SHORT).show()
            }
        }
    // 1. Объявляем клиент как свойство класса
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate started")
        
        try {
            enableEdgeToEdge() 
            handleIntent(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Basic init error", e)
        }

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        
        try {
            setContent {
                android.util.Log.d("MainActivity", "setContent block started")
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var showExit by remember { mutableStateOf(false) }
                val context = LocalContext.current
                
                // ViewModels инициализируем один раз
                val application = context.applicationContext as android.app.Application
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(application))
                val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(application))
                val cartViewModel: CartViewModel = viewModel(factory = CartViewModel.Factory(application))
                val chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel = viewModel(factory = com.business.gym.ui.viewmodel.ChatViewModel.Factory(application))
                val aboutViewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory(application))

                LaunchedEffect(Unit) {
                    authViewModel.loadSession(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val currentUserEmail by authViewModel.currentUserEmail
                val currentUid by authViewModel.currentUid
                val jwtToken by authViewModel.jwtToken

                LaunchedEffect(currentUserEmail, currentUid) {
                    if (!currentUid.isNullOrBlank()) {
                        settingsViewModel.loadSettings(context, currentUserEmail, currentUid)
                    }
                }

                LaunchedEffect(jwtToken, currentUid) {
                    if (jwtToken != null) {
                        cartViewModel.init(context, jwtToken, currentUid)
                        chatViewModel.startGlobalNotificationPolling(jwtToken)
                        authViewModel.startStatusPolling(context)
                    }
                }

                if (showSplash) {
                    SplashScreen(onFinished = { 
                        android.util.Log.d("MainActivity", "Splash finished")
                        showSplash = false 
                    })
                } else if (showExit) {
                    ExitScreen(onFinished = { 
                        // Полное закрытие приложения и всех его служб (включая плеер и фоновые процессы).
                        // Использование finishAffinity() гарантирует, что приложение будет выгружено из памяти.
                        finishAffinity()
                    })
                } else {
                    val controllerState = remember { mutableStateOf<MediaController?>(null) }
                    
                    DisposableEffect(Unit) {
                        controllerFuture?.addListener({
                            controllerState.value = controllerFuture?.get()
                        }, MoreExecutors.directExecutor())
                        onDispose { }
                    }

                    val player = controllerState.value
                    if (player == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.Red)
                        }
                    } else {
                        val themeMode by settingsViewModel.themeMode
                        val useDarkTheme = when (themeMode) {
                            "light" -> false
                            "dark" -> true
                            else -> isSystemInDarkTheme()
                        }

                        GymTheme(darkTheme = useDarkTheme) {
                            GymApp(
                                player = player, 
                                authViewModel = authViewModel, 
                                settingsViewModel = settingsViewModel, 
                                cartViewModel = cartViewModel,
                                chatViewModel = chatViewModel,
                                aboutViewModel = aboutViewModel,
                                navigationRequest = navigationRequest.value,
                                onResetNavigationRequest = { navigationRequest.value = null },
                                onExitRequest = { 
                                    player.stop() // Немедленно останавливаем музыку при запросе выхода
                                    showExit = true 
                                },
                                isDarkTheme = useDarkTheme
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "FATAL crash in setContent", e)
        }
    }

    private val navigationRequest = mutableStateOf<Pair<String, String?>?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        val senderId = intent?.getStringExtra("sender_id")
        if (navigateTo != null) {
            navigationRequest.value = navigateTo to senderId
        }
    }
    // 2. Метод для запуска чата (полностью исправленный)
    private fun startChat(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                client.webSocket(
                    host = "5.35.98.149",
                    port = 5557,
                    path = "/chat",
                    request = {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                ) {
                    println("Соединение установлено!")
                    // Отправка (убедитесь, что chatCipher доступен)
                    // send(Frame.Text(chatCipher.encrypt("Привет, админ!")))

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val encryptedText = frame.readText()
                            // val decrypted = chatCipher.decrypt(encryptedText)
                            withContext(Dispatchers.Main) {
                                println("Сообщение: $encryptedText")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Ошибка WebSocket: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Закрываем клиент и плеер
        client.close()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

@Composable
fun GymApp(
    player: Player,
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    cartViewModel: CartViewModel,
    chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel,
    aboutViewModel: AboutViewModel,
    navigationRequest: Pair<String, String?>?,
    onResetNavigationRequest: () -> Unit,
    onExitRequest: () -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserEmail by authViewModel.currentUserEmail
    val currentUid by authViewModel.currentUid
    val isSessionLoaded by authViewModel.isSessionLoaded
    val isAdmin by authViewModel.isAdminState

    if (!isSessionLoaded && !authViewModel.isGuest.value) {
        // Показываем загрузку только во время начальной проверки сессии
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.Red)
        }
    } else {
        GymAppContent(
            currentUserEmail = currentUserEmail,
            currentUid = currentUid ?: "", 
            isAdmin = isAdmin,
            player = player,
            onSignOut = { onSignOutAction ->
                // Остановка и очистка данных
                player.stop()
                chatViewModel.clearAll()
                settingsViewModel.clearProfile()
                cartViewModel.clearCart(context, sync = false)
                
                // Переход на главную, затем сброс авторизации
                coroutineScope.launch {
                    onSignOutAction() 
                    authViewModel.signOut()
                    authViewModel.clearSession(context)
                }
            },
            onSaveSession = { identifier -> },
            onExitRequest = onExitRequest,
            settingsViewModel = settingsViewModel,
            cartViewModel = cartViewModel,
            chatViewModel = chatViewModel,
            aboutViewModel = aboutViewModel,
            navigationRequest = navigationRequest,
            onResetNavigationRequest = onResetNavigationRequest,
            isDarkTheme = isDarkTheme,
            authViewModel = authViewModel
        )
    }
}

data class GymTab(
    val title: String,
    val icon: ImageVector,
    val key: String
)
@Composable
fun GymAppContent(
    currentUserEmail: String?,
    currentUid: String,
    isAdmin: Boolean,
    player: Player,
    onSignOut: (suspend () -> Unit) -> Unit,
    onSaveSession: (String) -> Unit,
    onExitRequest: () -> Unit,
    settingsViewModel: SettingsViewModel,
    cartViewModel: CartViewModel,
    chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel,
    aboutViewModel: AboutViewModel,
    navigationRequest: Pair<String, String?>?,
    onResetNavigationRequest: () -> Unit,
    isDarkTheme: Boolean,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isGuest by authViewModel.isGuest // Используем делегат для реактивности
    val privacyAgreed by settingsViewModel.privacyAgreed
    
    val newsTitle = stringResource(R.string.tab_news)
    val playlistTitle = stringResource(R.string.tab_playlist)
    val chatTitle = stringResource(R.string.tab_chat)
    val settingsTitle = stringResource(R.string.tab_settings)
    val shopTitle = stringResource(R.string.tab_shop)
    val aboutTitle = stringResource(R.string.tab_about)

    val tabs = remember(isGuest, privacyAgreed, newsTitle, playlistTitle, chatTitle, settingsTitle, shopTitle, aboutTitle) {
        val list = mutableListOf<GymTab>()
        if (!isGuest) {
            list.add(GymTab(newsTitle, Icons.Default.Newspaper, "news"))
            list.add(GymTab(playlistTitle, Icons.Default.PlayArrow, "playlist"))
            list.add(GymTab(chatTitle, Icons.AutoMirrored.Filled.Send, "chat"))
        }
        list.add(GymTab(shopTitle, Icons.Default.Store, "shop"))
        list.add(GymTab(aboutTitle, Icons.Default.Info, "about"))
        list.add(GymTab(settingsTitle, Icons.Default.AccountCircle, "settings"))
        list
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val jwtToken by authViewModel.jwtToken
    LaunchedEffect(navigationRequest, tabs) {
        navigationRequest?.let { (screen, id) ->
            if (screen == "chat") {
                val chatIndex = tabs.indexOfFirst { it.key == "chat" }
                if (chatIndex != -1) {
                    pagerState.scrollToPage(chatIndex)
                    if (id != null) {
                        val user = chatViewModel.users.value.find { it.uid == id || it.email == id }
                        if (user != null) {
                            chatViewModel.selectUser(user, currentUid ?: "", jwtToken)
                        }
                    }
                }
            }
            onResetNavigationRequest()
        }
    }
    
    var showAuthOverlay by rememberSaveable { mutableStateOf(false) }
    
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    GymBackground(isDark = isDarkTheme) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            if (currentUserEmail == null && !isGuest) {
                AuthScreen(
                    viewModel = authViewModel,
                    settingsViewModel = settingsViewModel,
                    onAuthSuccess = { email -> 
                        authViewModel.loadSession(context)
                    }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        modifier = Modifier.width(80.dp),
                        header = {
                            Icon(Icons.Default.FitnessCenter, null, tint = Color.Red, modifier = Modifier
                                .size(40.dp)
                                .padding(vertical = 8.dp))
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavigationRailItem(
                                    selected = pagerState.currentPage == index && !showAuthOverlay,
                                    onClick = { 
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        showAuthOverlay = false 
                                    },
                                    icon = { 
                                        if (tab.key == "cart") {
                                            val cartItems by cartViewModel.cartItems
                                            val count = cartItems.sumOf { it.second }
                                            BadgedBox(
                                                badge = {
                                                    if (count > 0) {
                                                        Badge(containerColor = Color.Red, contentColor = Color.White) {
                                                            Text("$count")
                                                        }
                                                    }
                                                }
                                            ) { Icon(tab.icon, contentDescription = tab.title) }
                                        } else if (tab.key == "chat") {
                                            val unreadCount = chatViewModel.notifiedCounts.value
                                                .filter { it.key != chatViewModel.selectedUser.value?.uid }
                                                .values.sumOf { if (it == 999999) 0 else it }
                                            
                                            BadgedBox(
                                                badge = {
                                                    if (unreadCount > 0) {
                                                        Badge(
                                                            containerColor = Color.Red,
                                                            contentColor = Color.White,
                                                            modifier = Modifier.offset(x = 8.dp, y = (-4).dp)
                                                        ) {
                                                            Text(if (unreadCount > 99) "99+" else "$unreadCount")
                                                        }
                                                    }
                                                }
                                            ) { Icon(tab.icon, contentDescription = tab.title) }
                                        } else {
                                            Icon(tab.icon, contentDescription = tab.title)
                                        }
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Color.Red,
                                        selectedTextColor = Color.Red,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            NavigationRailItem(
                                selected = showAuthOverlay,
                                onClick = { showAuthOverlay = true },
                                icon = { Icon(Icons.Default.AccountCircle, null) },
                                colors = NavigationRailItemDefaults.colors(selectedIconColor = Color.Red, indicatorColor = Color.Transparent)
                            )
                            NavigationRailItem(
                                selected = false,
                                onClick = { onExitRequest() },
                                icon = { Icon(Icons.Default.ExitToApp, null) },
                                colors = NavigationRailItemDefaults.colors(unselectedIconColor = Color.Gray)
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    // Используем WindowInsets.ime для поднятия контента над клавиатурой
                    contentWindowInsets = WindowInsets.systemBars,
                    topBar = { 
                        Column {
                            // Место под статус-бар
                            Spacer(Modifier
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                                .fillMaxWidth())
                        }
                    },
                    bottomBar = {
                        val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
                        if (!isWideScreen && !isKeyboardVisible) {
                            Column(modifier = Modifier.background(Color.Black.copy(alpha = 0.8f))) {
                                ScrollableTabRow(
                                    selectedTabIndex = pagerState.currentPage,
                                    divider = {},
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                    edgePadding = 8.dp,
                                    indicator = {
                                        TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pagerState.currentPage), color = Color.Red)
                                    }
                                ) {
                                    tabs.forEachIndexed { index, tab ->
                                        Tab(
                                            selected = pagerState.currentPage == index && !showAuthOverlay,
                                            onClick = { 
                                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                                showAuthOverlay = false 
                                            },
                                            text = { Text(tab.title, fontSize = 10.sp, maxLines = 1) },
                                            icon = { 
                                                if (tab.key == "chat") {
                                                    val unreadCount = chatViewModel.notifiedCounts.value
                                                        .filter { it.key != chatViewModel.selectedUser.value?.uid }
                                                        .values.sumOf { if (it == 999999) 0 else it }
                                                    
                                            BadgedBox(
                                                badge = {
                                                    if (unreadCount > 0) {
                                                        Badge(
                                                            containerColor = Color.Red,
                                                            contentColor = Color.White,
                                                            modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                                                        ) {
                                                            Text(
                                                                text = if (unreadCount > 99) "99+" else "$unreadCount",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    tab.icon, 
                                                    contentDescription = tab.title, 
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                                } else {
                                                    Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp))
                                                }
                                            },
                                            selectedContentColor = Color.Red,
                                            unselectedContentColor = Color.Gray
                                        )
                                    }
                                    if (currentUserEmail == null) {
                                        Tab(
                                            selected = showAuthOverlay,
                                            onClick = { showAuthOverlay = true },
                                            text = { Text(stringResource(R.string.auth_login_reg), fontSize = 10.sp, maxLines = 1) },
                                            icon = { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(20.dp)) },
                                            selectedContentColor = Color.Red,
                                            unselectedContentColor = Color.Gray
                                        )
                                    }
                                    Tab(
                                        selected = false,
                                        onClick = { onExitRequest() },
                                        text = { Text(stringResource(R.string.auth_exit), fontSize = 10.sp) },
                                        icon = { Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(20.dp)) },
                                        unselectedContentColor = Color.Gray
                                    )
                                }
                                Spacer(Modifier.height(40.dp))
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding() // Контент поднимается над клавиатурой
                    ) {
                        if (!showAuthOverlay) {
                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                val tabKey = if (page < tabs.size) tabs[page].key else ""
                                when (tabKey) {
                                    "news" -> {
                                        if (currentUserEmail == null || isGuest) {
                                            AuthScreen(
                                                viewModel = authViewModel,
                                                settingsViewModel = settingsViewModel,
                                                onAuthSuccess = { email -> 
                                                    authViewModel.loadSession(context)
                                                }
                                            )
                                        } else {
                                            NewsScreen(isAdmin = authViewModel.isAdmin(), authViewModel = authViewModel)
                                        }
                                    }
                                    "playlist" -> PlaylistScreen(
                                        player = player,
                                        isAdmin = authViewModel.isAdmin(),
                                        authViewModel = authViewModel
                                    )
                                    "chat" -> {
                                        if (currentUserEmail == null || isGuest) {
                                            AuthScreen(
                                                viewModel = authViewModel, 
                                                settingsViewModel = settingsViewModel, 
                                                onAuthSuccess = { email ->
                                                    authViewModel.loadSession(context)
                                                }
                                            )
                                        } else {
                                            val isRootAdmin by authViewModel.isRootAdminState
                                            ChatScreen(
                                                currentUid = currentUid ?: "", 
                                                isAdmin = authViewModel.isAdmin(), 
                                                viewModel = chatViewModel,
                                                isRootAdmin = isRootAdmin
                                            )
                                        }
                                    }
                                    "settings" -> SettingsScreen(
                                        currentUserEmail = currentUserEmail, 
                                        onLogout = { onSignOut { pagerState.scrollToPage(0) } },
                                        viewModel = settingsViewModel,
                                        authViewModel = authViewModel,
                                        onGoToCart = {
                                            val shopIndex = tabs.indexOfFirst { it.key == "shop" }
                                            if (shopIndex != -1) coroutineScope.launch { pagerState.animateScrollToPage(shopIndex) }
                                        }
                                    )
                                    "shop" -> ShopScreen(
                                        isAdmin = authViewModel.isAdmin(), 
                                        cartViewModel = cartViewModel,
                                        authViewModel = authViewModel
                                    )
                                    "about" -> AboutScreen(
                                        isAdmin = authViewModel.isAdmin(),
                                        viewModel = aboutViewModel,
                                        authViewModel = authViewModel
                                    )
                                }
                            }
                        }
                        if (showAuthOverlay) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AuthScreen(
                                    viewModel = authViewModel, 
                                    settingsViewModel = settingsViewModel, 
                                    onAuthSuccess = { email -> 
                                        showAuthOverlay = false
                                        authViewModel.loadSession(context)
                                    }
                                )
                                IconButton(onClick = { showAuthOverlay = false }, modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)) {
                                    Icon(Icons.Default.Clear, "Закрыть", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Preview(showBackground = true)
@Composable
fun GymAppPreview() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel = viewModel()
    val aboutViewModel: AboutViewModel = viewModel()
    GymTheme {
        val context = LocalContext.current
        val dummyPlayer = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
        GymAppContent(
            currentUserEmail = "test@example.com",
            currentUid = "123",
            isAdmin = false,
            player = dummyPlayer,
            onSignOut = {},
            onSaveSession = {},
            onExitRequest = {},
            settingsViewModel = settingsViewModel,
            cartViewModel = cartViewModel,
            chatViewModel = chatViewModel,
            aboutViewModel = aboutViewModel,
            navigationRequest = null,
            onResetNavigationRequest = {},
            isDarkTheme = true,
            authViewModel = authViewModel
        )
    }
}
