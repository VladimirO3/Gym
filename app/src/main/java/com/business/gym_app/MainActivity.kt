package com.business.gym_app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.business.gym_app.data.api.NewsApiService
import com.business.gym_app.receiver.ChatAlarmReceiver
import com.business.gym_app.service.ChatForegroundService
import com.business.gym_app.service.PlaybackService
import com.business.gym_app.ui.component.GymBackground
import com.business.gym_app.ui.component.ScrollableTabRow
import com.business.gym_app.ui.screen.AboutScreen
import com.business.gym_app.ui.screen.AuthScreen
import com.business.gym_app.ui.screen.ChatScreen
import com.business.gym_app.ui.screen.ExitScreen
import com.business.gym_app.ui.screen.NewsScreen
import com.business.gym_app.ui.screen.PlaylistScreen
import com.business.gym_app.ui.screen.SettingsScreen
import com.business.gym_app.ui.screen.ShopScreen
import com.business.gym_app.ui.screen.SplashScreen
import com.business.gym_app.ui.screen.WorkoutDetailScreen
import com.business.gym_app.ui.theme.GymTheme
import com.business.gym_app.ui.viewmodel.AboutViewModel
import com.business.gym_app.ui.viewmodel.AuthViewModel
import com.business.gym_app.ui.viewmodel.CartViewModel
import com.business.gym_app.ui.viewmodel.SettingsViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.analytics.FirebaseAnalytics
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var firebaseAnalytics: FirebaseAnalytics? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show()
                // После отказа системный диалог больше не показывается — уведомления о
                // сообщениях не придут. Открываем настройки приложения, чтобы пользователь
                // мог включить их вручную.
                com.business.gym_app.util.NotificationHelper.openNotificationSettings(this)
            }
        }
    // 1. Объявляем клиент как свойство класса
    private val client = HttpClient(OkHttp) {
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
                val chatViewModel: com.business.gym_app.ui.viewmodel.ChatViewModel = viewModel(factory = com.business.gym_app.ui.viewmodel.ChatViewModel.Factory(application))
                val aboutViewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory(application))

                LaunchedEffect(Unit) {
                    authViewModel.loadSession(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Запрос POST_NOTIFICATIONS из LaunchedEffect первой композиции
                        // роняет новое устройство IllegalStateException в
                        // registerForActivityResult (композиция еще не RESUMED).
                        // Откладываем до конца Splash — тогда Activity уже в RESUMED.
                        kotlinx.coroutines.delay(3500)
                        try {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "Notification permission request skipped", e)
                        }
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
                        // Слушатель обновлений не нужен гостю (сервер закрывает guest-сессию)
                        if (jwtToken != "guest_token") {
                            startUpdateListener(jwtToken!!)
                        }
                    }
                }

                // Тяжелые старты (foreground-сервис, alarm) — только ПОСЛЕ первой
                // композиции (Splash уже показан), иначе на новом устройстве
                // старт сервиса в onCreate роняет первую композицию:
                // WrappedComposition.setContent -> Lifecycle.addObserver ->
                // AndroidComposeView.onAttachedToWindow -> IllegalStateException.
                LaunchedEffect(jwtToken) {
                    if (jwtToken != null && jwtToken != "guest_token") {
                        // Периодический воркер — запасной канал проверки сообщений:
                        // планируем его ДО старта сервиса, потому что старт FGS из фона
                        // запрещен на Android 12+, а именно сервис и планировал воркер.
                        try {
                            com.business.gym_app.service.ChatCheckWorker.schedule(context)
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "ChatCheckWorker schedule failed", e)
                        }
                        try {
                            ChatAlarmReceiver.schedule(context)
                            val serviceIntent = Intent(context, ChatForegroundService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            // Android 12+ запрещает FGS-старт из фона —
                            // уведомления продолжит ChatCheckWorker.
                            android.util.Log.w("MainActivity", "Foreground service start skipped", e)
                        }
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
                        val useDarkTheme = true

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

    /**
     * Повторный запрос POST_NOTIFICATIONS при возврате в приложение.
     *
     * Разрешение запрашивалось один раз на splash: если пользователь его отклонил
     * или системный диалог не успел показаться, уведомления о сообщениях не приходят
     * до переустановки приложения. Здесь повторяем запрос, пока система это разрешает
     * (после двух отказов Android блокирует диалог — тогда остаются настройки).
     */
    override fun onResume() {
        super.onResume()
        com.business.gym_app.util.NotificationHelper.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !com.business.gym_app.util.NotificationHelper.areNotificationsEnabled(this) &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            try {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Notification permission re-request skipped", e)
            }
        }
    }

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
        // ... (existing code)
    }

    private var updateListenerJob: Job? = null
    private var updateListenerToken: String? = null

    /**
     * Слушатель обновлений через WebSocket (Шаг 4: Реализация на стороне клиента).
     * Защита от многократных переподключений:
     *  - guest-токен не подключается (сервер всё равно закроет соединение);
     *  - повторный запуск с тем же токеном игнорируется;
     *  - экспоненциальный backoff при ошибках (до 60 сек);
     *  - CancellationException пробрасывается, а не логируется как ошибка.
     */
    private fun startUpdateListener(token: String) {
        if (token.isBlank() || token == "guest_token") return
        if (updateListenerJob?.isActive == true && updateListenerToken == token) return
        updateListenerToken = token
        updateListenerJob?.cancel()
        updateListenerJob = lifecycleScope.launch(Dispatchers.IO) {
            var retryDelay = 5_000L
            while (isActive) {
                var connectedAt = 0L
                try {
                    val baseUrl = NewsApiService.getBaseUrl()
                    val hostPart = baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
                    val host = hostPart.substringBefore(":")
                    val port = hostPart.substringAfter(":", "").toIntOrNull()
                        ?: if (baseUrl.startsWith("https://")) 443 else 80
                    val protocol = if (baseUrl.startsWith("https://")) {
                        io.ktor.http.URLProtocol.WSS
                    } else {
                        io.ktor.http.URLProtocol.WS
                    }
                    client.webSocket(
                        method = io.ktor.http.HttpMethod.Get,
                        host = host,
                        port = port,
                        path = "/subscribe",
                        request = {
                            url.protocol = protocol
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                    ) {
                        android.util.Log.d("MainActivity", "Update subscription established")
                        connectedAt = System.currentTimeMillis()
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = frame.readText()
                                android.util.Log.d("MainActivity", "Update received: $message")
                                // Эмитим событие в общую шину
                                com.business.gym_app.util.AppEventBus.emit(message)
                            }
                        }
                    }
                    // Сервер закрыл соединение: если сессия жила дольше 10 сек —
                    // это была нормальная работа, сбрасываем backoff; если сервер
                    // закрыл почти сразу — растим паузу, чтобы не спамить переподключениями
                    val sessionDuration = System.currentTimeMillis() - connectedAt
                    retryDelay = if (connectedAt > 0 && sessionDuration >= 10_000L) {
                        5_000L
                    } else {
                        (retryDelay * 2).coerceAtMost(60_000L)
                    }
                    delay(retryDelay)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // Отмена job — не ошибка, пробрасываем дальше
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "WebSocket Update error: ${e.message}")
                    delay(retryDelay) // Пауза перед переподключением
                    retryDelay = (retryDelay * 2).coerceAtMost(60_000L) // Backoff до 60 сек
                }
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
    chatViewModel: com.business.gym_app.ui.viewmodel.ChatViewModel,
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
    chatViewModel: com.business.gym_app.ui.viewmodel.ChatViewModel,
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

    // id программы тренировок, открытой на экране просмотра (вкладка «Настройки»).
    // null — показывается обычный экран настроек.
    var openedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    
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
                    // В альбомной ориентации отключаем горизонтальные инсеты Scaffold, 
                    // так как у нас есть NavigationRail и мы хотим кастомное управление отступами.
                    // Сохраняем только вертикальные отступы (статус-бар и навигация снизу).
                    contentWindowInsets = if (isWideScreen) WindowInsets.systemBars.only(WindowInsetsSides.Vertical) else WindowInsets.systemBars,
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
                                    "settings" -> {
                                        if (openedWorkoutId != null) {
                                            val openedWorkout = settingsViewModel.customWorkouts.value
                                                .find { it.id == openedWorkoutId }
                                            LaunchedEffect(openedWorkoutId, openedWorkout) {
                                                // Программу не нашли (удалили / восстановление после убийства
                                                // процесса) — подгружаем список заново, состояние обновится
                                                // через Room-flow.
                                                if (openedWorkout == null) settingsViewModel.loadCustomWorkouts()
                                            }
                                            WorkoutDetailScreen(
                                                workout = openedWorkout,
                                                onBack = { openedWorkoutId = null }
                                            )
                                        } else {
                                            SettingsScreen(
                                                currentUserEmail = currentUserEmail, 
                                                onLogout = { onSignOut { pagerState.scrollToPage(0) } },
                                                viewModel = settingsViewModel,
                                                authViewModel = authViewModel,
                                                onGoToCart = {
                                                    val shopIndex = tabs.indexOfFirst { it.key == "shop" }
                                                    if (shopIndex != -1) coroutineScope.launch { pagerState.animateScrollToPage(shopIndex) }
                                                },
                                                onOpenWorkout = { openedWorkoutId = it }
                                            )
                                        }
                                    }
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
                                    Icon(Icons.Default.Clear, stringResource(R.string.close), tint = Color.Red)
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
    val chatViewModel: com.business.gym_app.ui.viewmodel.ChatViewModel = viewModel()
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
