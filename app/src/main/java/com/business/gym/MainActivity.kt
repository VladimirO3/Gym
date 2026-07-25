package com.business.gym

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.business.gym.ui.component.ScrollableTabRow
import com.business.gym.ui.navigation.GymNavGraph
import com.business.gym.ui.navigation.Screen
import com.business.gym.ui.screen.AuthScreen
import com.business.gym.ui.theme.GymTheme
import com.business.gym.ui.viewmodel.AuthViewModel
import com.business.gym.ui.viewmodel.SettingsViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.Firebase

/**
 * Главная точка входа в приложение Gym.
 *
 * Этот класс отвечает за:
 * - Инициализацию сервисов Firebase (Analytics, Firestore).
 * - Управление жизненным циклом медиаплеера (ExoPlayer).
 * - Настройку Jetpack Compose UI и темы.
 * - Инициализацию глобальных ViewModel (Auth, Settings).
 */
class MainActivity : AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Включение отображения контента под системными барами (статус-бар, навигация)
        enableEdgeToEdge()
        
        firebaseAnalytics = Firebase.analytics
        configureFirestore()

        setContent {
            // Инициализация ViewModel через Compose
            val authViewModel: AuthViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            val context = LocalContext.current
            
            // Управление жизненным циклом плеера внутри Compose
            val player = remember {
                ExoPlayer.Builder(context).build().also {
                    exoPlayer = it
                    mediaSession = MediaSession.Builder(context, it).build()
                }
            }

            // Освобождение ресурсов плеера при закрытии приложения
            DisposableEffect(Unit) {
                onDispose {
                    mediaSession?.release()
                    exoPlayer?.release()
                }
            }

            // Получение текущего e-mail пользователя из ViewModel
            val currentUserEmail by authViewModel.currentUserEmail
            
            // Автоматическая загрузка сохраненной сессии при старте
            LaunchedEffect(Unit) {
                authViewModel.loadSession(context)
            }
            
            // Загрузка настроек (тема и т.д.) при изменении пользователя
            LaunchedEffect(currentUserEmail) {
                settingsViewModel.loadSettings(context, currentUserEmail)
            }

            // Определение темы (светлая/темная) на основе настроек
            val themeMode by settingsViewModel.themeMode
            val useDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            // Применение кастомной темы GymTheme ко всему приложению
            GymTheme(darkTheme = useDarkTheme) {
                GymApp(player, authViewModel, settingsViewModel)
            }
        }
    }

    /**
     * Настройка параметров Firestore (отключение локального кэширования для этого проекта).
     */
    private fun configureFirestore() {
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Включено для работы в оффлайне
            .build()
        firestore.firestoreSettings = settings
    }
}

/**
 * Основной контейнер приложения, связывающий UI и ViewModel.
 */
@Composable
fun GymApp(
    exoPlayer: ExoPlayer,
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val currentUserEmail by authViewModel.currentUserEmail
    val currentUid by authViewModel.currentUid
    val isAdmin = remember(currentUserEmail) { authViewModel.isAdmin() }

    GymAppContent(
        currentUserEmail = currentUserEmail,
        onSignOut = {
            authViewModel.signOut()
            authViewModel.clearSession(context)
        },
        onSaveSession = { email ->
            authViewModel.saveSession(context, email)
        },
        // Лямбда для отрисовки основного контента (навигации)
        mainContent = { navController, innerPadding, currentRoute, setShowAuthOverlay ->
            GymNavGraph(
                navController = navController,
                exoPlayer = exoPlayer,
                isAdmin = isAdmin,
                currentUid = currentUid,
                currentUserEmail = currentUserEmail,
                onAuthSuccess = { email -> 
                    authViewModel.saveSession(context, email)
                    setShowAuthOverlay(false)
                    navController.navigate(currentRoute ?: Screen.News.route)
                },
                onLogout = {
                    authViewModel.signOut()
                    authViewModel.clearSession(context)
                    navController.navigate(Screen.News.route)
                },
                modifier = Modifier.padding(innerPadding)
            )
        },
        // Лямбда для отрисовки экрана авторизации
        authContent = { onAuthSuccess ->
            AuthScreen(onAuthSuccess = onAuthSuccess)
        }
    )
}

/**
 * Чистый UI-каркас приложения (Scaffold, BottomBar, Tabs).
 * Вынесен отдельно для возможности отображения в Preview.
 */
@Composable
fun GymAppContent(
    currentUserEmail: String?,
    onSignOut: () -> Unit,
    onSaveSession: (String) -> Unit,
    mainContent: @Composable (NavHostController, PaddingValues, String?, (Boolean) -> Unit) -> Unit,
    authContent: @Composable ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Состояние отображения оверлея авторизации
    var showAuthOverlay by rememberSaveable { mutableStateOf(false) }
    
    // Список вкладок нижней навигации
    val allTabs = listOf(
        Triple(stringResource(R.string.tab_news), Icons.Default.Info, Screen.News.route),
        Triple(stringResource(R.string.tab_playlist), Icons.Default.PlayArrow, Screen.Playlist.route),
        Triple(stringResource(R.string.tab_chat), Icons.AutoMirrored.Filled.Send, Screen.Chat.route),
        Triple(stringResource(R.string.tab_settings), Icons.Default.Settings, Screen.Settings.route), 
        Triple(stringResource(R.string.tab_about), Icons.Default.Add, Screen.About.route)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Пустой спейсер для корректного отображения под статус-баром
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth())
        },
        bottomBar = {
            // Нижняя панель навигации (Tabs)
            Column {
                ScrollableTabRow(
                    selectedTabIndex = allTabs.indexOfFirst { it.third == currentRoute }.takeIf { it != -1 } ?: 0,
                    divider = {},
                    indicator = {
                        val selectedIndex = allTabs.indexOfFirst { it.third == currentRoute }
                        if (selectedIndex != -1) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(selectedIndex),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    // Отрисовка основных вкладок
                    allTabs.forEach { (title, icon, route) ->
                        Tab(
                            selected = currentRoute == route,
                            onClick = { 
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                showAuthOverlay = false 
                            },
                            text = { Text(title, fontSize = 10.sp, maxLines = 1) },
                            icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp)) }
                        )
                    }
                    
                    // Кнопка Вход/Выход в конце списка вкладок
                    if (currentUserEmail == null) {
                        Tab(
                            selected = showAuthOverlay,
                            onClick = { showAuthOverlay = true },
                            text = { Text(stringResource(R.string.auth_login_reg), fontSize = 10.sp, maxLines = 1) },
                            icon = { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(20.dp)) }
                        )
                    } else {
                        Tab(
                            selected = false,
                            onClick = {
                                onSignOut()
                                navController.navigate(Screen.News.route)
                            },
                            text = { Text(stringResource(R.string.auth_logout), fontSize = 10.sp) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp)) }
                        )
                    }
                    
                    // Кнопка выхода из приложения
                    Tab(
                        selected = false,
                        onClick = { (context as? android.app.Activity)?.finish() },
                        text = { Text(stringResource(R.string.auth_exit), fontSize = 10.sp) },
                        icon = { Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(20.dp)) }
                    )
                }
                // Отступ снизу для красоты
                Spacer(Modifier.height(40.dp))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Отображение основного экрана через NavGraph
            mainContent(navController, innerPadding, currentRoute) { showAuthOverlay = it }

            // Оверлей авторизации (поверх всех экранов)
            if (showAuthOverlay) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    authContent { email ->
                        onSaveSession(email)
                        showAuthOverlay = false
                        navController.navigate(currentRoute ?: Screen.News.route)
                    }
                    // Кнопка закрытия окна авторизации
                    IconButton(
                        onClick = { showAuthOverlay = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/**
 * Превью для визуальной проверки интерфейса в Android Studio.
 */
@Preview(showBackground = true)
@Composable
fun GymAppPreview() {
    GymTheme {
        GymAppContent(
            currentUserEmail = "test@example.com",
            onSignOut = {},
            onSaveSession = {},
            mainContent = { _, innerPadding, _, _ ->
                Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Main Content Area")
                }
            },
            authContent = { _ ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Auth Screen")
                }
            }
        )
    }
}
