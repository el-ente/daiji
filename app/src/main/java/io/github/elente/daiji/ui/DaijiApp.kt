package io.github.elente.daiji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.Screen

private const val AMBIENT_RADIUS = 1600f

@Composable
fun DaijiApp(vm: MainViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()

    // The launcher is the bottom of the stack: Back never leaves it, it only returns Home.
    BackHandler(enabled = true) { vm.goHome() }

    DaijiTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(DaijiColors.Background)
                .background(
                    Brush.radialGradient(
                        colors = listOf(DaijiColors.Ambient, DaijiColors.Background),
                        center = Offset(AMBIENT_RADIUS * 0.35f, 0f),
                        radius = AMBIENT_RADIUS,
                    ),
                ),
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(vm)
                Screen.SEARCH -> SearchScreen(vm)
                Screen.ALL_GAMES -> GamesScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.SETUP -> SetupScreen(vm)
            }
        }
    }
}
