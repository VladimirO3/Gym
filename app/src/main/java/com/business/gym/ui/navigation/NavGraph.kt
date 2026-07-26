package com.business.gym.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.media3.exoplayer.ExoPlayer
import com.business.gym.ui.screen.*
import com.business.gym.ui.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    object News : Screen("news")
    object Playlist : Screen("playlist")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object About : Screen("about")
    object Privacy : Screen("privacy")
    object Auth : Screen("auth")
}

@Composable
fun GymNavGraph(
    navController: NavHostController,
    exoPlayer: ExoPlayer,
    isAdmin: Boolean,
    currentUid: String,
    currentUserEmail: String?,
    onAuthSuccess: (String) -> Unit,
    onLogout: () -> Unit,
    onPrivacyAgree: () -> Unit,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.News.route,
        modifier = modifier
    ) {
        composable(Screen.News.route) {
            NewsScreen(isAdmin = isAdmin)
        }
        composable(Screen.Playlist.route) {
            PlaylistScreen(exoPlayer = exoPlayer, isAdmin = isAdmin)
        }
        composable(Screen.Chat.route) {
            if (currentUserEmail == null) {
                AuthScreen(onAuthSuccess = onAuthSuccess)
            } else {
                ChatScreen(currentUid = currentUid, isAdmin = isAdmin)
            }
        }
        composable(Screen.Settings.route) {
            SettingsScreen(currentUserEmail = currentUserEmail, onLogout = onLogout)
        }
        composable(Screen.About.route) {
            AboutScreen(isAdmin = isAdmin)
        }
        composable(Screen.Privacy.route) {
            PrivacyScreen(
                onAgree = onPrivacyAgree,
                isAlreadyAgreed = settingsViewModel.privacyAgreed.value
            )
        }
        composable(Screen.Auth.route) {
            AuthScreen(onAuthSuccess = onAuthSuccess)
        }
    }
}
