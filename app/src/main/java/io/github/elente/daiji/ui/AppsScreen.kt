package io.github.elente.daiji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository

private const val APP_COLUMNS = 5

@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pinned by vm.pinnedApps.collectAsStateWithLifecycle()
    val dock = remember(apps, pinned) { AppsRepository.dock(apps, pinned).map { it.packageName }.toSet() }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }

    val memory = rememberFocusMemory { vm.focusMemory[Screen.ALL_APPS] = it }
    val gridState = rememberLazyGridState()
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        val saved = vm.focusMemory[Screen.ALL_APPS]
        val index = apps.indexOfFirst { it.packageName == saved }
        if (index > 0) gridState.scrollToItem(index)
        memory.restore(saved, apps.first().packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("All apps", apps.size)
        Text("Hold OK on an app to pin it to Home or open its info.", color = DaijiColors.Muted, fontSize = 13.sp)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(APP_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 40.dp),
        ) {
            items(apps, key = { it.packageName }, contentType = { "app" }) { app ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTile(
                        app = app,
                        onClick = { vm.launch(app) },
                        onLongClick = { actionsFor = app },
                        modifier = memory.tag(app.packageName)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                    Text(
                        text = if (app.packageName in dock) "★ ${app.label}" else app.label,
                        color = DaijiColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isPinned = app.packageName in dock,
            onTogglePin = { vm.togglePin(app) },
            onDismiss = { actionsFor = null },
        )
    }
}

@Composable
fun ScreenTitle(title: String, count: Int) {
    Text(
        text = "$title  ·  ${"%,d".format(count)}",
        color = DaijiColors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
    )
}

/** Hold OK or Menu on an app: pin or unpin it on the Home dock, or open its info page. */
@Composable
fun AppActionsDialog(app: AppEntry, isPinned: Boolean, onTogglePin: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .ignoreHeldSelect()
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DaijiColors.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(app.label, color = DaijiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            PillButton(
                text = if (isPinned) "Unpin from Home" else "Pin to Home",
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
                onClick = {
                    onTogglePin()
                    onDismiss()
                },
            )
            PillButton(text = "App info", modifier = Modifier.fillMaxWidth(), onClick = {
                AppsRepository.openInfo(context, app)
                onDismiss()
            })
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
