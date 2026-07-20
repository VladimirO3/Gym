package com.business.gym

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.business.gym.ui.component.ScrollableTabRow
import com.business.gym.ui.navigation.GymNavGraph
import com.business.gym.ui.navigation.Screen
import com.business.gym.ui.screen.AuthScreen
import com.business.gym.ui.theme.GymTheme
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var auth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        firebaseAnalytics = Firebase.analytics
        auth = FirebaseAuth.getInstance()
        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()

        setContent {
            GymApp(exoPlayer, auth)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
    }
}

@Composable
fun GymApp(exoPlayer: ExoPlayer? = null, auth: FirebaseAuth? = null) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    var themeMode by rememberSaveable { mutableStateOf("system") }
    var currentUserEmail by rememberSaveable { mutableStateOf(auth?.currentUser?.email ?: auth?.currentUser?.phoneNumber) }
    var showAuthOverlay by rememberSaveable { mutableStateOf(false) }
    
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }
    
    // Firebase Auth Listener
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                currentUserEmail = user.email ?: user.phoneNumber
            }
        }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    
    // Initial session check and theme/lang persistence
    LaunchedEffect(currentUserEmail) {
        if (currentUserEmail == null) {
            val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPref.getString("user_session_email", null)
            val savedTimestamp = sharedPref.getLong("user_session_timestamp", 0L)
            if (savedEmail != null && (System.currentTimeMillis() - savedTimestamp) < 3 * 24 * 60 * 60 * 1000L) {
                currentUserEmail = savedEmail
            }
        }

        val emailKey = currentUserEmail?.replace(".", "_") ?: "guest"
        val sharedPref = context.getSharedPreferences("settings_$emailKey", Context.MODE_PRIVATE)
        themeMode = sharedPref.getString("theme_mode", "system") ?: "system"
        val savedLang = sharedPref.getString("lang", "system") ?: "system"
        
        if (savedLang == "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }
    }

    val isAdmin = remember(currentUserEmail) { 
        currentUserEmail?.trim() == "+79530481451" 
    }

    GymTheme(darkTheme = useDarkTheme) {
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
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth())
            },
            bottomBar = {
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
                                    auth?.signOut()
                                    context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                                        .edit().remove("user_session_email").remove("user_session_timestamp").apply()
                                    currentUserEmail = null
                                    navController.navigate(Screen.News.route)
                                },
                                text = { Text(stringResource(R.string.auth_logout), fontSize = 10.sp) },
                                icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp)) }
                            )
                        }
                        
                        Tab(
                            selected = false,
                            onClick = { (context as? android.app.Activity)?.finish() },
                            text = { Text(stringResource(R.string.auth_exit), fontSize = 10.sp) },
                            icon = { Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(20.dp)) }
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                GymNavGraph(
                    navController = navController,
                    exoPlayer = exoPlayer!!,
                    isAdmin = isAdmin,
                    currentUid = auth?.currentUser?.uid ?: "",
                    currentUserEmail = currentUserEmail,
                    onAuthSuccess = { email -> 
                        currentUserEmail = email
                        showAuthOverlay = false
                        navController.navigate(currentRoute ?: Screen.News.route)
                    },
                    onLogout = {
                        auth?.signOut()
                        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                            .edit().remove("user_session_email").remove("user_session_timestamp").apply()
                        currentUserEmail = null
                        navController.navigate(Screen.News.route)
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                if (showAuthOverlay) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                        AuthScreen(
                            onAuthSuccess = { email ->
                                currentUserEmail = email
                                showAuthOverlay = false 
                            }
                        )
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
}
