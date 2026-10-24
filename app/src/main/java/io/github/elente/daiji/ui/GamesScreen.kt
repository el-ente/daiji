package io.github.elente.daiji.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.Library
import io.github.elente.daiji.data.Systems
import io.github.elente.daiji.data.matchingTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GAME_COLUMNS = 7
private const val GAMES_SETTINGS_PILL = "pill:settings"
private const val FAVOURITES = "favourites"
private val SearchWidth = 240.dp

private data class Filter(val id: String?, val label: String)

@Composable
fun GamesScreen(vm: MainViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasAccess by vm.hasStorageAccess.collectAsStateWithLifecycle()

    if (library.games.isEmpty()) {
        EmptyLibrary(
            scanning = scanning,
            hasAccess = hasAccess,
            hasRoot = settings.romRoot != null,
            onOpenSettings = { vm.open(Screen.GAMES_SETTINGS) },
        )
        return
    }

    val allLabel = stringResource(R.string.filter_all)
    val favouritesLabel = stringResource(R.string.filter_favourites)
    val filters = remember(library, favourites.isEmpty(), allLabel, favouritesLabel) {
        filters(library, hasFavourites = favourites.isNotEmpty(), allLabel, favouritesLabel)
    }
    var filterId by remember { mutableStateOf(vm.allGamesFilter?.takeIf { id -> filters.any { it.id == id } }) }
    var query by remember { mutableStateOf(vm.allGamesQuery) }
    val games = remember(library, favourites, filterId, query) {
        when (filterId) {
            null -> library.games
            FAVOURITES -> library.games.filter { it.path in favourites }
            else -> library.bySystem[filterId].orEmpty()
        }.matchingTitle(query) { it.title }
    }
    val focusedGame = remember { mutableStateOf<Game?>(null) }
    val selectedPill = remember { FocusRequester() }

    val memory = rememberFocusMemory { vm.focusMemory[Screen.ALL_GAMES] = it }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val saved = vm.focusMemory[Screen.ALL_GAMES]
        val index = games.indexOfFirst { it.path == saved }
        gridState.scrollToItem(index.coerceAtLeast(0))
        memory.restore(saved?.takeIf { index >= 0 || it == GAMES_SETTINGS_PILL }, games.firstOrNull()?.path)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(stringResource(R.string.all_games), library.games.size, Modifier.weight(1f))
            GlassPill(
                text = stringResource(R.string.games_settings),
                icon = Icons.Filled.Settings,
                modifier = memory.tag(GAMES_SETTINGS_PILL),
                onClick = { vm.open(Screen.GAMES_SETTINGS) },
            )
        }
        FocusedGameLine(focusedGame, favourites)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterPills(filters, filterId, selectedPill, Modifier.weight(1f)) { id ->
                filterId = id
                vm.allGamesFilter = id
                scope.launch { gridState.scrollToItem(0) }
            }
            SearchField(
                query = query,
                onQueryChange = {
                    query = it
                    vm.allGamesQuery = it
                },
                modifier = Modifier.width(SearchWidth),
            )
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(GAME_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 40.dp),
        ) {
            itemsIndexed(games, key = { _, g -> g.path }, contentType = { _, _ -> "game" }) { index, game ->
                GameTile(
                    game = game,
                    onClick = { vm.launch(game) },
                    onLongClick = { vm.toggleFavourite(game) },
                    modifier = memory.tag(game.path)
                        .onFocusChanged { if (it.isFocused) focusedGame.value = game }
                        .focusProperties { if (index < GAME_COLUMNS) up = selectedPill }
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                )
            }
        }
    }
}

/** All first, then Favourites, then each system with more than one game; a lone game stays under All. */
private fun filters(library: Library, hasFavourites: Boolean, allLabel: String, favouritesLabel: String): List<Filter> = buildList {
    add(Filter(null, allLabel))
    if (hasFavourites) add(Filter(FAVOURITES, favouritesLabel))
    library.systems
        .filter { library.bySystem[it.id].orEmpty().size > 1 }
        .forEach { add(Filter(it.id, it.shortName)) }
}

/** The focused game's name and system above the grid. Reads the focus state itself, so only this line recomposes. */
@Composable
private fun FocusedGameLine(focusedGame: MutableState<Game?>, favourites: Map<String, Long>) {
    val game = focusedGame.value
    val system = game?.let { Systems.byId(it.systemId) }
    val star = if (game != null && game.path in favourites) "★  " else ""
    Text(
        text = game?.let { listOfNotNull(star + it.title, system?.name, it.info.year?.toString()).joinToString("  ·  ") }
            ?: stringResource(R.string.favourite_hint),
        color = DaijiColors.Muted,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pills switch on OK, not on focus, so moving across them doesn't reload the grid each step. */
@Composable
private fun FilterPills(
    filters: List<Filter>,
    selectedId: String?,
    selectedRequester: FocusRequester,
    modifier: Modifier,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { filter ->
            val selected = filter.id == selectedId
            FocusCard(
                onClick = { onSelect(filter.id) },
                modifier = if (selected) Modifier.focusRequester(selectedRequester) else Modifier,
                shape = RoundedCornerShape(50),
                container = if (selected) DaijiColors.Glass else Color.Transparent,
                focusedContainer = DaijiColors.SurfaceHigh,
                focusedScale = 1.0f,
            ) {
                Text(
                    text = filter.label,
                    color = if (selected) DaijiColors.Text else DaijiColors.Muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    scanning: Boolean,
    hasAccess: Boolean,
    hasRoot: Boolean,
    onOpenSettings: () -> Unit,
) {
    val button = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) {
            if (runCatching { button.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(32)
        }
    }
    val message = when {
        scanning -> stringResource(R.string.no_games_scanning)
        !hasAccess -> stringResource(R.string.no_games_no_access)
        !hasRoot -> stringResource(R.string.no_games_no_root)
        else -> stringResource(R.string.no_games_none_found)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.no_games_title), color = DaijiColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(message, color = DaijiColors.Muted, fontSize = 15.sp)
        PillButton(
            text = stringResource(R.string.games_settings),
            onClick = onOpenSettings,
            primary = true,
            modifier = Modifier
                .padding(top = 8.dp)
                .focusRequester(button),
        )
    }
}

@Composable
fun ScreenTitle(title: String, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$title  ·  ${"%,d".format(count)}",
        color = DaijiColors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        modifier = modifier,
    )
}
