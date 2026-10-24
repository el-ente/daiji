package io.github.elente.daiji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.Tab

@Composable
fun DaijiApp(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()

    // The launcher is the bottom of the stack: Back never leaves it.
    BackHandler(enabled = true) { vm.goHome() }

    DaijiTheme {
        Row(
            Modifier
                .fillMaxSize()
                .background(DaijiColors.Background),
        ) {
            NavRail(selected = tab, onSelect = vm::selectTab)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                when (tab) {
                    Tab.GAMES -> GamesScreen(vm)
                    Tab.APPS -> AppsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                    Tab.SETUP -> SetupScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun NavRail(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(DaijiColors.Rail)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RailItem(Icons.Filled.PlayArrow, "Games", selected == Tab.GAMES) { onSelect(Tab.GAMES) }
        RailItem(Icons.Filled.Menu, "Apps", selected == Tab.APPS) { onSelect(Tab.APPS) }
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Filled.Settings, "Settings", selected == Tab.SETTINGS) { onSelect(Tab.SETTINGS) }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        selected -> DaijiColors.OnAccent
        focused -> DaijiColors.Text
        else -> DaijiColors.Muted
    }
    FocusCard(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        container = if (selected) DaijiColors.Accent else Color.Transparent,
        focusedContainer = if (selected) DaijiColors.AccentFocused else DaijiColors.SurfaceHigh,
        focusedScale = 1.1f,
        showRing = false,
    ) {
        Image(
            imageVector = icon,
            contentDescription = label,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp),
        )
    }
}
