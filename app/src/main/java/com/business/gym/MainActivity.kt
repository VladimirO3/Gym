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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
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
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.Firebase
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase
import com.business.gym.data.api.NewsApiService
import kotlinx.coroutines.launch
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * Главная Activity приложения. 
 * Управляет жизненным циклом плеера, разрешениями и навигацией верхнего уровня.
 */
class MainActivity : AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    // Лаунчер для запроса разрешений на уведомления (Android 13+)
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Уведомления отключены", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Включение полноэкранного режима (под шторку)
        
        // Обработка входящего интента (например, переход из уведомления)
        handleIntent(intent)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        firebaseAnalytics = Firebase.analytics

        // Создаем плеер ОДИН РАЗ на уровне Activity, чтобы музыка не прерывалась при смене экранов
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true) // true = обработка фокуса аудио
                .setMediaSourceFactory(NewsApiService.getMediaSourceFactory(this))
                .build()

            // MediaSession позволяет управлять плеером через шторку и заблокированный экран
            // Используем уникальный ID для каждой сессии, чтобы избежать IllegalStateException
            mediaSession = MediaSession.Builder(this, exoPlayer!!)
                .setId("GymAppSession_${System.currentTimeMillis()}")
                .build()
        }

        setContent {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            var showExit by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val application = context.applicationContext as android.app.Application
            
            // Инициализация основных ViewModel
            val authViewModel: AuthViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(application)
            )
            val cartViewModel: CartViewModel = viewModel()
            val chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel = viewModel(
                factory = com.business.gym.ui.viewmodel.ChatViewModel.Factory(application)
            )
            
            // Загружаем сессию СРАЗУ при запуске
            LaunchedEffect(Unit) {
                authViewModel.loadSession(context)
            }

            val currentUserEmail by authViewModel.currentUserEmail
            val currentUid by authViewModel.currentUid
            val jwtToken by authViewModel.jwtToken

            // Загрузка настроек пользователя при изменении его статуса
            LaunchedEffect(currentUserEmail, currentUid) {
                settingsViewModel.loadSettings(context, currentUserEmail, currentUserEmail)
            }

            // Инициализация корзины и поллинга чата при получении токена
            LaunchedEffect(jwtToken) {
                cartViewModel.init(context, jwtToken)
                chatViewModel.startGlobalNotificationPolling(jwtToken)
                authViewModel.startStatusPolling(context)
            }

            if (showSplash) {
                SplashScreen(onFinished = { showSplash = false })
            } else if (showExit) {
                ExitScreen(onFinished = { (context as? android.app.Activity)?.finish() })
            } else {
                val player = remember { exoPlayer!! }
                val themeMode by settingsViewModel.themeMode
                val useDarkTheme = when (themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                GymTheme(darkTheme = useDarkTheme) {
                    GymApp(
                        exoPlayer = player, 
                        authViewModel = authViewModel, 
                        settingsViewModel = settingsViewModel, 
                        cartViewModel = cartViewModel,
                        chatViewModel = chatViewModel,
                        navigationRequest = navigationRequest.value,
                        onResetNavigationRequest = { navigationRequest.value = null },
                        onExitRequest = { showExit = true },
                        isDarkTheme = useDarkTheme
                    )
                }
            }
        }
    }

    // Состояние запроса на навигацию (используется для диплинков из уведомлений)
    private val navigationRequest = mutableStateOf<Pair<String, String?>?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Разбор параметров интента для перехода на нужный экран.
     */
    private fun handleIntent(intent: android.content.Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        val senderId = intent?.getStringExtra("sender_id")
        if (navigateTo != null) {
            navigationRequest.value = navigateTo to senderId
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Освобождение ресурсов плеера при закрытии приложения
        mediaSession?.release()
        exoPlayer?.release()
        mediaSession = null
        exoPlayer = null
    }
}

/**
 * Точка входа в UI Compose. Настраивает основные компоненты и передает зависимости.
 */
@Composable
fun GymApp(
    exoPlayer: ExoPlayer,
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    cartViewModel: CartViewModel,
    chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel,
    navigationRequest: Pair<String, String?>?,
    onResetNavigationRequest: () -> Unit,
    onExitRequest: () -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserEmail by authViewModel.currentUserEmail
    val currentUid by authViewModel.currentUid
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }

    GymAppContent(
        currentUserEmail = currentUserEmail,
        currentUid = currentUid,
        isAdmin = isAdmin,
        exoPlayer = exoPlayer,
        onSignOut = { onSignOutAction ->
            exoPlayer.stop()
            authViewModel.signOut()
            authViewModel.clearSession(context)
            cartViewModel.clearCart(context, sync = false)
            coroutineScope.launch {
                onSignOutAction()
            }
        },
        onSaveSession = { identifier ->
            // Дополнительная логика при сохранении сессии
        },
        onExitRequest = onExitRequest,
        settingsViewModel = settingsViewModel,
        cartViewModel = cartViewModel,
        chatViewModel = chatViewModel,
        navigationRequest = navigationRequest,
        onResetNavigationRequest = onResetNavigationRequest,
        isDarkTheme = isDarkTheme,
        authViewModel = authViewModel
    )
}

/**
 * Модель вкладки нижнего меню.
 */
data class GymTab(
    val title: String,
    val icon: ImageVector,
    val key: String
)

/**
 * Основной контент приложения с навигацией (Pager) и нижним меню.
 */
@Composable
fun GymAppContent(
    currentUserEmail: String?,
    currentUid: String,
    isAdmin: Boolean,
    exoPlayer: ExoPlayer,
    onSignOut: (suspend () -> Unit) -> Unit, // Изменил сигнатуру
    onSaveSession: (String) -> Unit,
    onExitRequest: () -> Unit,
    settingsViewModel: SettingsViewModel,
    cartViewModel: CartViewModel,
    chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel,
    navigationRequest: Pair<String, String?>?,
    onResetNavigationRequest: () -> Unit,
    isDarkTheme: Boolean,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isGuest = authViewModel.isGuest.value
    
    // Динамический список вкладок в зависимости от статуса гостя
    val tabs = remember(isGuest) {
        val list = mutableListOf<GymTab>()
        list.add(GymTab("Новости", Icons.Default.Newspaper, "news"))
        list.add(GymTab("Музыка", Icons.Default.PlayArrow, "playlist"))
        if (!isGuest) {
            list.add(GymTab("Чат", Icons.AutoMirrored.Filled.Send, "chat"))
        }
        list.add(GymTab("Профиль", Icons.Default.AccountCircle, "settings"))
        list.add(GymTab("Магазин", Icons.Default.Store, "shop"))
        list.add(GymTab("Оферта", Icons.Default.Gavel, "privacy"))
        list.add(GymTab("О приложении", Icons.Default.Add, "about"))
        list
    }

    // Состояние пайджера для свайпов между экранами
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // Реакция на внешние запросы навигации (например, открытие чата из уведомления)
    val jwtToken by authViewModel.jwtToken
    LaunchedEffect(navigationRequest, tabs) {
        navigationRequest?.let { (screen, id) ->
            if (screen == "chat") {
                val chatIndex = tabs.indexOfFirst { it.key == "chat" }
                if (chatIndex != -1) {
                    pagerState.scrollToPage(chatIndex)
                    if (id != null) {
                        val user = chatViewModel.users.value.find { it.uid == id }
                        if (user != null) {
                            chatViewModel.selectUser(user, currentUid, jwtToken)
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
            Row(modifier = Modifier.fillMaxSize()) {
                // Боковое меню для планшетов (NavigationRail)
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        modifier = Modifier.width(80.dp),
                        header = {
                            Icon(
                                Icons.Default.FitnessCenter, 
                                null, 
                                tint = Color.Red,
                                modifier = Modifier.size(40.dp).padding(vertical = 8.dp)
                            )
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
                                            ) {
                                                Icon(tab.icon, contentDescription = tab.title)
                                            }
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
                            
                            // Кнопка авторизации (если не вошли)
                            NavigationRailItem(
                                selected = showAuthOverlay,
                                onClick = { showAuthOverlay = true },
                                icon = { Icon(Icons.Default.AccountCircle, null) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color.Red,
                                    selectedTextColor = Color.Red,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color.Transparent
                                )
                            )
                            // Кнопка выхода из приложения
                            NavigationRailItem(
                                selected = false,
                                onClick = { onExitRequest() },
                                icon = { Icon(Icons.Default.ExitToApp, null) },
                                colors = NavigationRailItemDefaults.colors(
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                // Основная область экрана со Scaffold
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    topBar = {
                        // Резерв места под статус-бар
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth())
                    },
                    bottomBar = {
                        // Нижнее меню для телефонов (скрываем при открытой клавиатуре)
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
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(pagerState.currentPage),
                                            color = Color.Red
                                        )
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
                                                Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp))
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
                                // Нижний отступ для эстетичного вида на безрамочных экранах
                                Spacer(Modifier.height(40.dp))
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (!showAuthOverlay) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val tabKey = if (page < tabs.size) tabs[page].key else ""
                                when (tabKey) {
                                    "news" -> {
                                        if (currentUserEmail == null || isGuest) {
                                            AuthScreen(
                                                viewModel = authViewModel,
                                                settingsViewModel = settingsViewModel,
                                                onAuthSuccess = { onSaveSession(it) }
                                            )
                                        } else {
                                            NewsScreen(
                                                isAdmin = isAdmin,
                                                authViewModel = authViewModel
                                            )
                                        }
                                    }
                                    "playlist" -> PlaylistScreen(exoPlayer = exoPlayer, isAdmin = isAdmin)
                                    "chat" -> {
                                        if (currentUserEmail == null) {
                                            AuthScreen(
                                                viewModel = authViewModel,
                                                settingsViewModel = settingsViewModel,
                                                onAuthSuccess = { onSaveSession(it) }
                                            )
                                        } else {
                                            ChatScreen(
                                                currentUid = currentUid, 
                                                isAdmin = isAdmin,
                                                viewModel = chatViewModel
                                            )
                                        }
                                    }
                                    "settings" -> SettingsScreen(
                                        currentUserEmail = currentUserEmail, 
                                        onLogout = {
                                            onSignOut {
                                                pagerState.scrollToPage(0)
                                            }
                                        },
                                        viewModel = settingsViewModel,
                                        authViewModel = authViewModel,
                                        onGoToCart = {
                                            val shopIndex = tabs.indexOfFirst { it.key == "shop" }
                                            if (shopIndex != -1) {
                                                coroutineScope.launch { pagerState.animateScrollToPage(shopIndex) }
                                            }
                                        }
                                    )
                                    "shop" -> ShopScreen(
                                        isAdmin = isAdmin,
                                        cartViewModel = cartViewModel,
                                        onGoToCart = {
                                            // onGoToCart now handled internally in ShopScreen or just navigation
                                        }
                                    )
                                    "privacy" -> {
                                        val currentContext = LocalContext.current
                                        PrivacyScreen(
                                            onAgree = { settingsViewModel.setPrivacyAgreed(currentContext, currentUserEmail, true) },
                                            isAlreadyAgreed = settingsViewModel.privacyAgreed.value
                                        )
                                    }
                                    "about" -> AboutScreen(isAdmin = isAdmin)
                                }
                            }
                        }

                        // Слой авторизации поверх основного контента (если выбран)
                        if (showAuthOverlay) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AuthScreen(
                                    viewModel = authViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onAuthSuccess = { email ->
                                        onSaveSession(email)
                                        showAuthOverlay = false
                                    }
                                )
                                IconButton(
                                    onClick = { showAuthOverlay = false },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                ) {
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

@Preview(showBackground = true)
@Composable
fun GymAppPreview() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel = viewModel()
    val navReq: Pair<String, String?>? = null
    GymTheme {
        GymAppContent(
            currentUserEmail = "test@example.com",
            currentUid = "123",
            isAdmin = false,
            exoPlayer = ExoPlayer.Builder(LocalContext.current).build(),
            onSignOut = {},
            onSaveSession = {},
            onExitRequest = {},
            settingsViewModel = settingsViewModel,
            cartViewModel = cartViewModel,
            chatViewModel = chatViewModel,
            navigationRequest = navReq,
            onResetNavigationRequest = {},
            isDarkTheme = true,
            authViewModel = authViewModel
        )
    }
}
