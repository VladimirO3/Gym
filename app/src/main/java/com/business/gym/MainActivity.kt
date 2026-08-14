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
import com.business.gym.ui.viewmodel.AboutViewModel
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

class MainActivity : AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var firebaseAnalytics: FirebaseAnalytics? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Уведомления отключены", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate started")
        
        try {
            enableEdgeToEdge() 
            handleIntent(intent)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Init error", e)
        }

        try {
            firebaseAnalytics = Firebase.analytics
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Analytics error", e)
        }

        if (exoPlayer == null) {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                exoPlayer = ExoPlayer.Builder(this)
                    .setAudioAttributes(audioAttributes, true)
                    .setMediaSourceFactory(NewsApiService.getMediaSourceFactory(this))
                    .build()

                mediaSession = MediaSession.Builder(this, exoPlayer!!)
                    .setId("GymAppSession_${System.currentTimeMillis()}")
                    .build()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Player init error", e)
            }
        }

        setContent {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            var showExit by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val application = context.applicationContext as android.app.Application
            
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(application)
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(application)
            )
            val cartViewModel: CartViewModel = viewModel(
                factory = CartViewModel.Factory(application)
            )
            val chatViewModel: com.business.gym.ui.viewmodel.ChatViewModel = viewModel(
                factory = com.business.gym.ui.viewmodel.ChatViewModel.Factory(application)
            )
            val aboutViewModel: AboutViewModel = viewModel(
                factory = AboutViewModel.Factory(application)
            )
            
            LaunchedEffect(Unit) {
                authViewModel.loadSession(context)
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
                SplashScreen(onFinished = { showSplash = false })
            } else if (showExit) {
                ExitScreen(onFinished = { (context as? android.app.Activity)?.finish() })
            } else {
                val player = exoPlayer ?: remember {
                    ExoPlayer.Builder(context).build()
                }
                
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
                        aboutViewModel = aboutViewModel,
                        navigationRequest = navigationRequest.value,
                        onResetNavigationRequest = { navigationRequest.value = null },
                        onExitRequest = { showExit = true },
                        isDarkTheme = useDarkTheme
                    )
                }
            }
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

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        exoPlayer?.release()
        mediaSession = null
        exoPlayer = null
    }
}

@Composable
fun GymApp(
    exoPlayer: ExoPlayer,
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
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }

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
            exoPlayer = exoPlayer,
            onSignOut = { onSignOutAction ->
                // Остановка и очистка данных
                exoPlayer.stop()
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
    exoPlayer: ExoPlayer,
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
    
    val tabs = remember(isGuest, privacyAgreed) {
        val list = mutableListOf<GymTab>()
        list.add(GymTab("Новости", Icons.Default.Newspaper, "news"))
        list.add(GymTab("Плейлист", Icons.Default.PlayArrow, "playlist"))
        if (!isGuest) {
            list.add(GymTab("Чат", Icons.AutoMirrored.Filled.Send, "chat"))
        }
        list.add(GymTab("Профиль", Icons.Default.AccountCircle, "settings"))
        list.add(GymTab("Магазин", Icons.Default.Store, "shop"))
        
        list.add(GymTab("О нас", Icons.Default.Info, "about"))
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
                        val user = chatViewModel.users.value.find { it.uid == id }
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
            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        modifier = Modifier.width(80.dp),
                        header = {
                            Icon(Icons.Default.FitnessCenter, null, tint = Color.Red, modifier = Modifier.size(40.dp).padding(vertical = 8.dp))
                        }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
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
                            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth())
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
                                            NewsScreen(isAdmin = isAdmin, authViewModel = authViewModel)
                                        }
                                    }
                                    "playlist" -> PlaylistScreen(exoPlayer = exoPlayer, isAdmin = isAdmin)
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
                                            ChatScreen(currentUid = currentUid ?: "", isAdmin = isAdmin, viewModel = chatViewModel)
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
                                    "shop" -> ShopScreen(isAdmin = isAdmin, cartViewModel = cartViewModel)
                                    "about" -> AboutScreen(isAdmin = isAdmin, viewModel = aboutViewModel)
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
                                IconButton(onClick = { showAuthOverlay = false }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
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
    val aboutViewModel: AboutViewModel = viewModel()
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
            aboutViewModel = aboutViewModel,
            navigationRequest = null,
            onResetNavigationRequest = {},
            isDarkTheme = true,
            authViewModel = authViewModel
        )
    }
}
