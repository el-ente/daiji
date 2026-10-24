package io.github.elente.daiji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository

private const val APP_COLUMNS = 6

/** Every installed app, A to Z. Hold OK on one to pin it to Home or unpin it. */
@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pinned by vm.pinnedApps.collectAsStateWithLifecycle()
    val home = remember(apps, pinned) { AppsRepository.home(apps, pinned).toSet() }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }

    val memory = rememberFocusMemory { vm.focusMemory[Screen.ALL_APPS] = it }
    val gridState = rememberLazyGridState()
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        val saved = vm.focusMemory[Screen.ALL_APPS]
        gridState.scrollToItem(apps.indexOfFirst { it.packageName == saved }.coerceAtLeast(0))
        memory.restore(saved, apps.first().packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.all_apps), apps.size)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(APP_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 40.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTile(
                        app = app,
                        onClick = { vm.launch(app) },
                        onLongClick = { actionsFor = app },
                        modifier = memory.tag(app.packageName).fillMaxWidth().aspectRatio(16f / 9f),
                    )
                    AppLabel(app, Modifier.fillMaxWidth())
                }
            }
        }
    }
    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isPinned = app in home,
            onTogglePin = { vm.togglePin(app) },
            onDismiss = { actionsFor = null },
        )
    }
}
