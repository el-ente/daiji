package io.github.elente.daiji.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.Tab
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.Systems
import io.github.elente.daiji.launch.Emulators
import kotlinx.coroutines.delay

private data class Shelf(val id: String, val label: String, val games: List<Game>)

private val CoverWidth = 96.dp
private val CoverHeight = 132.dp
private val HeroArtWidth = 420.dp
private val HeroArtHeight = 236.dp

@Composable
fun GamesScreen(vm: MainViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasAccess by vm.hasStorageAccess.collectAsStateWithLifecycle()

    val shelves = remember(library, recents, favourites) {
        buildList {
            val recentGames = recents.entries
                .sortedByDescending { it.value }
                .mapNotNull { library.byPath[it.key] }
            if (recentGames.isNotEmpty()) add(Shelf("recent", "Recent", recentGames))
            val favouriteGames = library.games.filter { it.path in favourites }
            if (favouriteGames.isNotEmpty()) add(Shelf("favourites", "Favourites", favouriteGames))
            library.systems.forEach { s -> add(Shelf(s.id, s.shortName, library.bySystem[s.id].orEmpty())) }
        }
    }

    if (shelves.isEmpty()) {
        EmptyLibrary(
            scanning = scanning,
            hasAccess = hasAccess,
            hasRoot = settings.romRoot != null,
            onOpenSettings = { vm.selectTab(Tab.SETTINGS) },
        )
        return
    }

    var selectedShelfId by rememberSaveable { mutableStateOf(shelves.first().id) }
    val shelf = shelves.firstOrNull { it.id == selectedShelfId } ?: shelves.first()
    var focusedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val hero = focusedPath?.let { library.byPath[it] } ?: shelf.games.firstOrNull()

    val firstCover = remember { FocusRequester() }
    // Up from a cover and Down from the hero go to the selected tab, not the nearest one.
    val selectedTab = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Land on the first game when the launcher opens.
        repeat(5) {
            if (runCatching { firstCover.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(32)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (hero != null) {
            val system = Systems.byId(hero.systemId)
            Hero(
                game = hero,
                lastPlayed = recents[hero.path],
                emulatorName = system?.let { Emulators.resolve(it, settings).name } ?: "",
                isFavourite = hero.path in favourites,
                onPlay = { vm.launch(hero) },
                onShuffle = { vm.randomGame(excludingPath = hero.path)?.let { focusedPath = it.path } },
                onToggleFavourite = { vm.toggleFavourite(hero) },
                onCycleEmulator = { system?.let { vm.cycleEmulator(it) } },
                downTarget = selectedTab,
            )
        }

        ShelfTabs(
            shelves = shelves,
            selectedId = shelf.id,
            selectedRequester = selectedTab,
            onSelect = {
                if (it != selectedShelfId) {
                    selectedShelfId = it
                    focusedPath = null
                }
            },
        )

        key(shelf.id) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) {
                itemsIndexed(shelf.games, key = { _, g -> g.path }) { index, game ->
                    GameCover(
                        game = game,
                        modifier = (if (index == 0) Modifier.focusRequester(firstCover) else Modifier)
                            .focusProperties { up = selectedTab },
                        onFocused = { focusedPath = game.path },
                        onClick = { vm.launch(game) },
                        onLongClick = { vm.toggleFavourite(game) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(
    game: Game,
    lastPlayed: Long?,
    emulatorName: String,
    isFavourite: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavourite: () -> Unit,
    onCycleEmulator: () -> Unit,
    downTarget: FocusRequester,
) {
    val system = Systems.byId(game.systemId)
    val density = LocalDensity.current
    val artW = with(density) { HeroArtWidth.roundToPx() }
    val artH = with(density) { HeroArtHeight.roundToPx() }
    val art = rememberFileImage(game.screenshot ?: game.cover, artW, artH)

    val played = if (lastPlayed != null) {
        "Last played " + DateUtils.getRelativeTimeSpanString(
            lastPlayed,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
    } else {
        "Not played yet"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroArtHeight),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                system?.let { Chip(it.shortName) }
                if (game.extension.isNotEmpty()) Chip(game.extension)
            }
            Text(
                text = game.title,
                color = DaijiColors.Text,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 40.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(system?.name, played).joinToString(" · "),
                color = DaijiColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val toTabs = Modifier.focusProperties { down = downTarget }
                PillButton(text = "Play", onClick = onPlay, primary = true, modifier = toTabs)
                PillButton(text = "Random", onClick = onShuffle, modifier = toTabs)
                PillButton(
                    text = if (isFavourite) "★ Favourite" else "☆ Favourite",
                    onClick = onToggleFavourite,
                    modifier = toTabs,
                )
                PillButton(text = emulatorName, onClick = onCycleEmulator, modifier = toTabs)
            }
        }
        Box(
            modifier = Modifier
                .size(HeroArtWidth, HeroArtHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(DaijiColors.placeholderFor(game.title)),
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = game.title,
                    color = DaijiColors.Text.copy(alpha = 0.55f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text = text,
        color = DaijiColors.Text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DaijiColors.Surface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ShelfTabs(
    shelves: List<Shelf>,
    selectedId: String,
    selectedRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    // A plain scrolling Row keeps every tab composed, so the selected tab's
    // FocusRequester is always attached.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shelves.forEach { shelf ->
            val selected = shelf.id == selectedId
            FocusCard(
                onClick = { onSelect(shelf.id) },
                modifier = (if (selected) Modifier.focusRequester(selectedRequester) else Modifier)
                    .onFocusChanged { if (it.isFocused) onSelect(shelf.id) },
                shape = RoundedCornerShape(50),
                container = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainer = DaijiColors.SurfaceHigh,
                focusedScale = 1.0f,
                showRing = false,
            ) {
                Text(
                    text = shelf.label,
                    color = if (selected) DaijiColors.Text else DaijiColors.Muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun GameCover(
    game: Game,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val density = LocalDensity.current
    val w = with(density) { CoverWidth.roundToPx() }
    val h = with(density) { CoverHeight.roundToPx() }
    val cover = rememberFileImage(game.cover, w, h)

    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(CoverWidth, CoverHeight)
            .onFocusChanged { if (it.isFocused) onFocused() },
        container = DaijiColors.placeholderFor(game.title),
        focusedScale = 1.08f,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = game.title,
                color = DaijiColors.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
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
        scanning -> "Scanning your ROMs…"
        !hasAccess -> "Daiji needs storage access to read your ROMs."
        !hasRoot -> "Put your ROMs in a folder like ROMs/snes or ROMs/psx, then pick that folder in Settings."
        else -> "No games found. Each system needs its own subfolder, like ROMs/snes or ROMs/gba."
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text("No games yet", color = DaijiColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(message, color = DaijiColors.Muted, fontSize = 15.sp)
        PillButton(
            text = "Open settings",
            onClick = onOpenSettings,
            primary = true,
            modifier = Modifier
                .padding(top = 8.dp)
                .focusRequester(button),
        )
    }
}
