package io.github.elente.daiji.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository
import io.github.elente.daiji.data.matchingTitle

private const val FIELD = "field"
private val GameResultWidth = 120.dp
private val AppResultWidth = 160.dp
private val AppResultHeight = 90.dp

/** Games and apps whose title contains the query, one row each. */
@Composable
fun SearchScreen(vm: MainViewModel) {
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pinned by vm.pinnedApps.collectAsStateWithLifecycle()
    val games = remember(library, query) { library.games.matchingTitle(query) { it.title } }
    val foundApps = remember(apps, query) { apps.matchingTitle(query) { it.label } }
    val home = remember(apps, pinned) { AppsRepository.home(apps, pinned).map { it.packageName }.toSet() }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }

    val memory = rememberFocusMemory { vm.focusMemory[Screen.SEARCH] = it }
    LaunchedEffect(Unit) { memory.restore(vm.focusMemory[Screen.SEARCH], FIELD) }
    val firstRow = remember { FocusRequester() }
    val hasResults = query.isNotBlank() && (games.isNotEmpty() || foundApps.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SearchField(
            query = query,
            onQueryChange = vm::setSearchQuery,
            placeholder = "Search games and apps",
            modifier = memory.tag(FIELD)
                .focusProperties { if (hasResults) down = firstRow }
                .fillMaxWidth(),
        )
        when {
            query.isBlank() -> Hint("Type a title to find a game or an app.")
            games.isEmpty() && foundApps.isEmpty() -> Hint("Nothing matches “${query.trim()}”.")
            else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (games.isNotEmpty()) {
                    ResultRow("Games", games.size, Modifier.focusRequester(firstRow)) {
                        items(games, key = { it.path }) { game ->
                            GameTile(
                                game = game,
                                onClick = { vm.launch(game) },
                                onLongClick = { vm.openDetail(game) },
                                modifier = memory.tag(game.path).width(GameResultWidth).aspectRatio(3f / 4f),
                            )
                        }
                    }
                }
                if (foundApps.isNotEmpty()) {
                    ResultRow("Apps", foundApps.size, if (games.isEmpty()) Modifier.focusRequester(firstRow) else Modifier) {
                        items(foundApps, key = { it.packageName }) { app ->
                            AppTile(
                                app = app,
                                onClick = { vm.launch(app) },
                                onLongClick = { actionsFor = app },
                                modifier = memory.tag(app.packageName).size(AppResultWidth, AppResultHeight),
                            )
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isPinned = app.packageName in home,
            onTogglePin = { vm.togglePin(app) },
            onDismiss = { actionsFor = null },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DaijiColors.Muted, fontSize = 15.sp)
}

@Composable
private fun ResultRow(label: String, count: Int, modifier: Modifier, tiles: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${label.uppercase()}  ·  $count", color = DaijiColors.Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        LazyRow(
            modifier = modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            content = tiles,
        )
    }
}
