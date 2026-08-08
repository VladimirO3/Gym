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

class MainActivity : AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        firebaseAnalytics = Firebase.analytics

        // Создаем плеер ОДИН РАЗ на уровне Activity, чтобы он не пересоздавался при смене ориентации
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true) // true = handleAudioFocus
                .setMediaSourceFactory(NewsApiService.getMediaSourceFactory(this))
                .build()

            mediaSession = MediaSession.Builder(this, exoPlayer!!).build()
        }

        setContent {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            var showExit by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val application = context.applicationContext as android.app.Application
            
            val authViewModel: AuthViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(application)
            )
            val cartViewModel: CartViewModel = viewModel()
            
            if (showSplash) {
                SplashScreen(onFinished = { showSplash = false })
            } else if (showExit) {
                ExitScreen(onFinished = { (context as? android.app.Activity)?.finish() })
            } else {
                val player = remember { exoPlayer!! }
                val currentUserEmail by authViewModel.currentUserEmail
                val currentUid by authViewModel.currentUid
                val jwtToken by authViewModel.jwtToken
                
                LaunchedEffect(Unit) {
                    authViewModel.loadSession(context)
                }

                LaunchedEffect(jwtToken) {
                    cartViewModel.init(jwtToken)
                }
                
                LaunchedEffect(currentUserEmail, currentUid) {
                    // Мы больше не зависим от UID из Firebase, используем email как ключ настроек
                    settingsViewModel.loadSettings(context, currentUserEmail, currentUserEmail)
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
                        onExitRequest = { showExit = true },
                        isDarkTheme = useDarkTheme
                    )
                }
            }
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
    onExitRequest: () -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val currentUserEmail by authViewModel.currentUserEmail
    val currentUid by authViewModel.currentUid
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }

    GymAppContent(
        currentUserEmail = currentUserEmail,
        currentUid = currentUid,
        isAdmin = isAdmin,
        exoPlayer = exoPlayer,
        onSignOut = {
            authViewModel.signOut()
            authViewModel.clearSession(context)
            cartViewModel.clearCart(sync = false)
        },
        onSaveSession = { identifier ->
            // Сессия уже сохранена внутри ViewModel.verifyOtp, 
            // здесь мы просто можем выполнить дополнительные действия если нужно.
        },
        onExitRequest = onExitRequest,
        settingsViewModel = settingsViewModel,
        cartViewModel = cartViewModel,
        isDarkTheme = isDarkTheme,
        authViewModel = authViewModel
    )
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
    onSignOut: () -> Unit,
    onSaveSession: (String) -> Unit,
    onExitRequest: () -> Unit,
    settingsViewModel: SettingsViewModel,
    cartViewModel: CartViewModel,
    isDarkTheme: Boolean,
    authViewModel: AuthViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val isGuest = authViewModel.isGuest.value
    
    // Список вкладок (Заголовок, Иконка, Ключ)
    val tabs = remember(isGuest) {
        val list = mutableListOf<GymTab>()
        list.add(GymTab("Новости", Icons.Default.Newspaper, "news"))
        list.add(GymTab("Плейлист", Icons.Default.PlayArrow, "playlist"))
        
        if (!isGuest) {
            list.add(GymTab("Чат", Icons.AutoMirrored.Filled.Send, "chat"))
            list.add(GymTab("Корзина", Icons.Default.ShoppingCart, "cart"))
        }
        
        list.add(GymTab("Настройки", Icons.Default.Settings, "settings"))
        list.add(GymTab("Магазин", Icons.Default.Store, "shop"))
        list.add(GymTab("Право", Icons.Default.Gavel, "privacy"))
        list.add(GymTab("О нас", Icons.Default.Add, "about"))
        list
    }

    // Состояние пайджера для свайпов
    val pagerState = rememberPagerState(pageCount = { tabs.size })
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

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    topBar = {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth())
                    },
                    bottomBar = {
                        if (!isWideScreen) {
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
                                                        Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp))
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
                                    } else {
                                        Tab(
                                            selected = false,
                                            onClick = {
                                                onSignOut()
                                            },
                                            text = { Text(stringResource(R.string.auth_logout), fontSize = 10.sp) },
                                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp)) },
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
                                            ChatScreen(currentUid = currentUid, isAdmin = isAdmin)
                                        }
                                    }
                                    "cart" -> CartScreen(cartViewModel = cartViewModel)
                                    "settings" -> SettingsScreen(currentUserEmail = currentUserEmail, onLogout = onSignOut)
                                    "shop" -> ShopScreen(
                                        isAdmin = isAdmin,
                                        cartViewModel = cartViewModel,
                                        onGoToCart = {
                                            val cartIndex = tabs.indexOfFirst { it.key == "cart" }
                                            if (cartIndex != -1) {
                                                coroutineScope.launch { pagerState.animateScrollToPage(cartIndex) }
                                            }
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
                                    Icon(Icons.Default.Clear, "Close", tint = Color.Red)
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
            isDarkTheme = true,
            authViewModel = authViewModel
        )
    }
}
