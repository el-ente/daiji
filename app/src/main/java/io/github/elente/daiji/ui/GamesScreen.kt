@file:OptIn(ExperimentalComposeUiApi::class)

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.delay

private const val GAMES_SETTINGS_PILL = "pill:settings"
private const val SEARCH_PILL = "pill:search"
private const val FAVOURITES = "favourites"
private const val ACCLAIMED = "acclaimed"
private val GridFade = 16.dp

/** Covers wait until a platform switch has settled, so their decoding doesn't stutter the new grid. */
private const val ART_DELAY_AFTER_SWITCH_MS = 300L

private data class Filter(val id: String?, val label: String)

/** A grid cell: a game in the library, or an acclaimed game the user doesn't own, shown so they know it exists. */
private sealed interface GridItem {
    val game: Game
    val key: String get() = game.path

    data class Owned(override val game: Game) : GridItem
    data class NotOwned(override val game: Game) : GridItem
}

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

    val acclaimedView by vm.acclaimedView.collectAsStateWithLifecycle()
    val acclaimedOwned = acclaimedView.owned
    val acclaimedNotOwned = remember(acclaimedView) { acclaimedView.notOwned.map(GridItem::NotOwned) }
    val labels = FilterLabels(
        all = stringResource(R.string.filter_all),
        favourites = stringResource(R.string.filter_favourites),
        acclaimed = stringResource(R.string.filter_acclaimed),
    )
    val hasAcclaimed = acclaimedOwned.isNotEmpty() || acclaimedNotOwned.isNotEmpty()
    val filters = remember(library, favourites.isEmpty(), hasAcclaimed, labels) {
        filters(library, hasFavourites = favourites.isNotEmpty(), hasAcclaimed, labels)
    }
    var filterId by remember { mutableStateOf(vm.allGamesFilter?.takeIf { id -> filters.any { it.id == id } }) }
    val items: List<GridItem> = remember(library, favourites, filterId, acclaimedOwned, acclaimedNotOwned) {
        when (filterId) {
            null -> library.games.map(GridItem::Owned)
            FAVOURITES -> library.games.filter { it.path in favourites }.map(GridItem::Owned)
            ACCLAIMED -> acclaimedOwned.map(GridItem::Owned) + acclaimedNotOwned
            else -> library.bySystem[filterId].orEmpty().map(GridItem::Owned)
        }
    }
    val notInLibrary = stringResource(R.string.not_in_your_library)
    val cell = remember(filterId) { gridCell(Systems.byId(filterId.orEmpty())?.boxArtRatio) }
    val selectedPill = remember { FocusRequester() }

    val memory = rememberFocusMemory { vm.focusMemory[Screen.ALL_GAMES] = it }
    val gridState = rememberLazyGridState()
    var hasRestoredFocus by remember { mutableStateOf(false) }
    var isArtReady by remember { mutableStateOf(true) }
    LaunchedEffect(filterId) {
        if (hasRestoredFocus) {
            gridState.scrollToItem(0)
            delay(ART_DELAY_AFTER_SWITCH_MS)
            isArtReady = true
            return@LaunchedEffect
        }
        hasRestoredFocus = true
        val saved = vm.focusMemory[Screen.ALL_GAMES]
        val index = items.indexOfFirst { it.key == saved }
        gridState.scrollToItem(index.coerceAtLeast(0))
        memory.restore(saved?.takeIf { index >= 0 || it == GAMES_SETTINGS_PILL || it == SEARCH_PILL }, items.firstOrNull()?.key)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (filterId == ACCLAIMED) {
                val count = stringResource(R.string.acclaimed_count, acclaimedOwned.size, items.size)
                ScreenTitle(stringResource(R.string.all_games), count, Modifier.weight(1f))
            } else {
                ScreenTitle(stringResource(R.string.all_games), items.size, Modifier.weight(1f))
            }
            GlassPill(
                text = stringResource(R.string.search_games),
                icon = Glyphs.Search,
                modifier = memory.tag(SEARCH_PILL).focusProperties { down = selectedPill },
                onClick = { vm.open(Screen.SEARCH) },
            )
            GlassPill(
                text = stringResource(R.string.games_settings),
                icon = Icons.Filled.Settings,
                modifier = memory.tag(GAMES_SETTINGS_PILL).focusProperties { down = selectedPill },
                onClick = { vm.open(Screen.GAMES_SETTINGS) },
            )
        }
        FilterPills(filters, filterId, selectedPill, memory.requester(SEARCH_PILL), Modifier.fillMaxWidth()) { id ->
            if (id == filterId) return@FilterPills
            isArtReady = false
            filterId = id
            vm.allGamesFilter = id
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(cell.columns),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = GridFade, bottom = 40.dp),
            modifier = Modifier.fadeTopEdge(GridFade),
        ) {
            itemsIndexed(items, key = { _, item -> item.key }, contentType = { _, _ -> "game" }) { index, item ->
                var focused by remember { mutableStateOf(false) }
                val game = item.game
                val isOwned = item is GridItem.Owned
                val showNotInLibrary = { vm.toast(notInLibrary.format(game.title)) }
                GameTile(
                    game = game,
                    onClick = if (isOwned) ({ vm.launch(game) }) else showNotInLibrary,
                    onLongClick = if (isOwned) ({ vm.openDetail(game) }) else showNotInLibrary,
                    marker = if (game.path in favourites) "★" else null,
                    showCaption = focused,
                    artScale = ContentScale.Fit,
                    loadArt = isArtReady,
                    isOwned = isOwned,
                    modifier = memory.tag(item.key)
                        .onFocusChanged { focused = it.isFocused }
                        .focusProperties { if (index < cell.columns) up = selectedPill }
                        .fillMaxWidth()
                        .aspectRatio(cell.ratio),
                )
            }
        }
    }
}

private data class FilterLabels(val all: String, val favourites: String, val acclaimed: String)

/** All first, then Favourites and Acclaimed, then each system with more than one game; a lone game stays under All. */
private fun filters(library: Library, hasFavourites: Boolean, hasAcclaimed: Boolean, labels: FilterLabels): List<Filter> = buildList {
    add(Filter(null, labels.all))
    if (hasFavourites) add(Filter(FAVOURITES, labels.favourites))
    if (hasAcclaimed) add(Filter(ACCLAIMED, labels.acclaimed))
    library.systems
        .filter { library.bySystem[it.id].orEmpty().size > 1 }
        .forEach { add(Filter(it.id, it.shortName)) }
}

/** Each system's grid takes the shape of its box art, so covers show whole; All mixes shapes in portrait cells. */
private data class GridCell(val ratio: Float, val columns: Int)

private fun gridCell(boxArtRatio: Float?): GridCell = when {
    boxArtRatio == null -> GridCell(3f / 4f, 7)
    boxArtRatio > 1.2f -> GridCell(boxArtRatio, 5)
    boxArtRatio > 0.9f -> GridCell(boxArtRatio, 6)
    else -> GridCell(boxArtRatio, 7)
}

/** Rows scrolling under the filter pills fade out instead of being cut. */
private fun Modifier.fadeTopEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, height.toPx() to Color.Black, startY = 0f, endY = height.toPx()),
            blendMode = BlendMode.DstIn,
        )
    }

/** Pills switch on OK, not on focus, so moving across them doesn't reload the grid each step. */
@Composable
private fun FilterPills(
    filters: List<Filter>,
    selectedId: String?,
    selectedRequester: FocusRequester,
    upRequester: FocusRequester,
    modifier: Modifier,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEachIndexed { index, filter ->
            val selected = filter.id == selectedId
            val isFirst = index == 0
            val isLast = index == filters.lastIndex
            FocusCard(
                onClick = { onSelect(filter.id) },
                modifier = Modifier
                    .then(if (selected) Modifier.focusRequester(selectedRequester) else Modifier)
                    .focusProperties {
                        up = upRequester
                        if (isFirst) left = FocusRequester.Cancel
                        if (isLast) right = FocusRequester.Cancel
                    },
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
fun ScreenTitle(title: String, count: Int, modifier: Modifier = Modifier) =
    ScreenTitle(title, "%,d".format(count), modifier)

@Composable
fun ScreenTitle(title: String, detail: String, modifier: Modifier = Modifier) {
    Text(
        text = "$title  ·  $detail",
        color = DaijiColors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        modifier = modifier,
    )
}
