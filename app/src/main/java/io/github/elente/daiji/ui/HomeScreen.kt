@file:OptIn(ExperimentalFoundationApi::class)

package io.github.elente.daiji.ui

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import io.github.elente.daiji.data.Network
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.Library
import io.github.elente.daiji.data.Systems
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val GLOW_ALPHA = 0.30f
private val GlowCenterX = 180.dp
private val GlowCenterY = 150.dp
private val GlowRadius = 420.dp
private val DockTileWidth = 160.dp
private val DockTileHeight = 90.dp
private const val GRID_COLUMNS = 6
private val GridTileWidth = 130.dp
private val GridTileHeight = 73.dp
private val BAND_PADDING = 16.dp
private val TOP_FADE = 40.dp
private val SCROLL_ROOM = 360.dp
private const val SHELF_RECENTS = 3
private const val SHELF_FAVOURITES = 8
private const val SHORT_DATE = DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_NO_YEAR

@Composable
fun HomeScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pinned by vm.pinnedApps.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val randomPick by vm.randomPick.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()

    val dock = remember(apps, pinned) { AppsRepository.dock(apps, pinned) }
    val unpinned = remember(apps, dock) { apps - dock.toSet() }
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }
    val shelf = remember(recents, favourites, library, randomPick) { gameShelf(library, recents, favourites, randomPick) }
    val focused = remember { mutableStateOf<HomeFocus?>(null) }
    val memory = rememberFocusMemory { vm.focusMemory[Screen.HOME] = it }
    // Apps load a moment after Home appears. Restoring earlier would land on a placeholder, and remember it.
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        memory.restore(vm.focusMemory[Screen.HOME], dock.firstOrNull()?.let(::dockKey), "entry:games")
    }

    val scroll = rememberScrollState()
    val sections = remember { SectionScroller(scroll) }
    val rowScrolling = LocalBringIntoViewSpec.current

    AmbientGlow(focused)
    // The page scrolls by whole sections (SectionScroller), never by the few pixels that bring a tile into view and
    // leave the row above cut in half. The games row gets the default back, so it still scrolls sideways.
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoPageScroll) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                network = network,
                memory = memory,
                onOpenSearch = { vm.open(Screen.SEARCH) },
                onOpenSettings = { vm.open(Screen.SETTINGS) },
            )
            Column(sections.section(SectionScroller.TOP), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DockTitle(focused)
                Dock(
                    apps = dock,
                    appsLoaded = apps.isNotEmpty(),
                    memory = memory,
                    focused = focused,
                    onLaunch = vm::launch,
                    onShowActions = { actionsFor = it },
                )
            }
            Column(sections.section("games"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTitle(focused)
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrolling) {
                    GamesShelf(
                        items = shelf,
                        gameCount = library.games.size,
                        memory = memory,
                        focused = focused,
                        onPlay = vm::launch,
                        onToggleFavourite = vm::toggleFavourite,
                        onReroll = vm::rerollRandom,
                        onOpenAll = { vm.open(Screen.ALL_GAMES) },
                    )
                }
            }
            AppsGrid(
                apps = unpinned,
                memory = memory,
                focused = focused,
                sections = sections,
                onLaunch = vm::launch,
                onShowActions = { actionsFor = it },
            )
            // Room for the last section to scroll to the top like the others, instead of stopping half way.
            Spacer(Modifier.height(SCROLL_ROOM))
        }
    }
    TopFade(scroll)
    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isPinned = app in dock,
            onTogglePin = { vm.togglePin(app) },
            onDismiss = { actionsFor = null },
        )
    }
}

private fun dockKey(app: AppEntry) = "dock:${app.packageName}"

@Composable
private fun Header(network: String, memory: FocusMemory, onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Clock(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPill(text = "Search", icon = Glyphs.Search, modifier = memory.tag("pill:search"), onClick = onOpenSearch)
            if (network.isNotEmpty()) {
                GlassPill(text = networkLabel(network), icon = networkIcon(network), modifier = memory.tag("pill:network"), onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
            }
            GlassPill(text = stringResource(R.string.settings), icon = Icons.Filled.Settings, modifier = memory.tag("pill:settings"), onClick = onOpenSettings)
        }
    }
}

@Composable
private fun networkLabel(label: String): String = when (label) {
    Network.WIFI -> stringResource(R.string.network_wifi)
    Network.ETHERNET -> stringResource(R.string.network_ethernet)
    Network.OFFLINE -> stringResource(R.string.network_offline)
    Network.ONLINE -> stringResource(R.string.network_online)
    else -> label
}

/** The Wi-Fi glyph for a Wi-Fi network; Ethernet and Offline read clearly enough as words. */
private fun networkIcon(label: String): ImageVector? =
    if (label == Network.ETHERNET || label == Network.OFFLINE || label == Network.ONLINE) null else Glyphs.Wifi

/** Greeting, a large thin clock and the date. Ticks once a minute, on the minute, and recomposes only itself. */
@Composable
private fun Clock(modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DateUtils.MINUTE_IN_MILLIS - now % DateUtils.MINUTE_IN_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    val is24h = DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(if (is24h) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
    Column(modifier) {
        Text(stringResource(greeting(now)).uppercase(), color = DaijiColors.Label, fontSize = 14.sp, letterSpacing = 3.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                time,
                color = DaijiColors.Text,
                fontSize = 52.sp,
                fontWeight = FontWeight.Thin,
                lineHeight = 54.sp,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                date.uppercase(),
                color = DaijiColors.Label,
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@StringRes
private fun greeting(now: Long): Int = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..19 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

/**
 * A glow in the colour of what has focus (an app's banner, a game's system), spread over the whole screen so it has
 * no edges. Reads the focus state itself, so only the glow recomposes when focus moves.
 */
@Composable
private fun AmbientGlow(focused: MutableState<HomeFocus?>) {
    val density = LocalDensity.current
    val focus = focused.value
    val appTone = rememberAppTone(
        (focus as? HomeFocus.App)?.app,
        with(density) { AppArtWidth.roundToPx() },
        with(density) { AppArtHeight.roundToPx() },
    )
    val tone = (focus as? HomeFocus.Game)?.let { DaijiColors.systemTone(it.item.game.systemId) } ?: appTone ?: return
    val center = with(density) { Offset(GlowCenterX.toPx(), GlowCenterY.toPx()) }
    val radius = with(density) { GlowRadius.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(tone.copy(alpha = GLOW_ALPHA), Color.Transparent), center, radius)),
    )
}

/** The focused app's name in large light type. */
@Composable
private fun DockTitle(focused: MutableState<HomeFocus?>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = (focused.value as? HomeFocus.App)?.app?.label.orEmpty(),
            color = DaijiColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    appsLoaded: Boolean,
    memory: FocusMemory,
    focused: MutableState<HomeFocus?>,
    onLaunch: (AppEntry) -> Unit,
    onShowActions: (AppEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DaijiColors.DockBand)
            .padding(BAND_PADDING),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (appsLoaded && apps.isEmpty()) {
            Text(
                stringResource(R.string.dock_empty),
                color = DaijiColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.height(DockTileHeight).padding(start = 8.dp, top = 34.dp),
            )
        }
        apps.forEach { app ->
            HomeAppTile(app, dockKey(app), memory, focused, onLaunch, onShowActions)
        }
    }
}

/** Every app not pinned to the dock, smaller than the dock's so they read as secondary. Each row scrolls into place. */
@Composable
private fun AppsGrid(
    apps: List<AppEntry>,
    memory: FocusMemory,
    focused: MutableState<HomeFocus?>,
    sections: SectionScroller,
    onLaunch: (AppEntry) -> Unit,
    onShowActions: (AppEntry) -> Unit,
) {
    apps.chunked(GRID_COLUMNS).forEachIndexed { index, row ->
        Column(sections.section("apps:$index"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index == 0) SectionLabel(stringResource(R.string.section_apps))
            Row(modifier = Modifier.padding(horizontal = BAND_PADDING), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { app ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeAppTile(app, "app:${app.packageName}", memory, focused, onLaunch, onShowActions, GridTileWidth, GridTileHeight)
                        // The dock's large name is scrolled away down here, and many apps only have an icon.
                        Text(
                            text = app.label,
                            color = DaijiColors.Label,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(GridTileWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = DaijiColors.Label, fontSize = 14.sp, letterSpacing = 2.sp)
}

/** Fades the page out under the top edge once it has scrolled, so a section's top never ends in a hard cut. */
@Composable
private fun TopFade(scroll: ScrollState) {
    if (scroll.value == 0) return
    Box(
        Modifier
            .fillMaxWidth()
            .height(TOP_FADE)
            .background(Brush.verticalGradient(listOf(DaijiColors.Background, Color.Transparent))),
    )
}

@Composable
private fun HomeAppTile(
    app: AppEntry,
    key: String,
    memory: FocusMemory,
    focused: MutableState<HomeFocus?>,
    onLaunch: (AppEntry) -> Unit,
    onShowActions: (AppEntry) -> Unit,
    width: Dp = DockTileWidth,
    height: Dp = DockTileHeight,
) {
    AppTile(
        app = app,
        onClick = { onLaunch(app) },
        onLongClick = { onShowActions(app) },
        modifier = memory.tag(key)
            .onFocusChanged { if (it.isFocused) focused.value = HomeFocus.App(app) }
            .size(width, height),
    )
}

/** What has focus on Home, for the titles and the glow. */
private sealed interface HomeFocus {
    data class App(val app: AppEntry) : HomeFocus
    data class Game(val item: ShelfGame) : HomeFocus
}

private enum class ShelfKind(val marker: String) { RECENT("▶"), FAVOURITE("★"), RANDOM("🎲") }

private data class ShelfGame(val game: Game, val kind: ShelfKind, val lastPlayed: Long?)

/** Recently played first, then favourites not already shown, then the random pick. */
private fun gameShelf(library: Library, recents: Map<String, Long>, favourites: Map<String, Long>, random: Game?): List<ShelfGame> {
    val recent = recents.entries.sortedByDescending { it.value }
        .mapNotNull { (path, time) -> library.byPath[path]?.let { ShelfGame(it, ShelfKind.RECENT, time) } }
        .take(SHELF_RECENTS)
    val shown = recent.map { it.game.path }.toSet()
    val favourite = favourites.entries.sortedByDescending { it.value }
        .mapNotNull { (path, _) -> library.byPath[path]?.takeIf { path !in shown } }
        .take(SHELF_FAVOURITES)
        .map { ShelfGame(it, ShelfKind.FAVOURITE, recents[it.path]) }
    return recent + favourite + listOfNotNull(random?.let { ShelfGame(it, ShelfKind.RANDOM, recents[it.path]) })
}

private fun shelfKey(item: ShelfGame) = "game:${item.kind}:${item.game.path}"

/** The focused game, its system and when it was played; a quiet label while focus is elsewhere. */
@Composable
private fun ShelfTitle(focused: MutableState<HomeFocus?>) {
    val item = (focused.value as? HomeFocus.Game)?.item
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.BottomStart) {
        if (item == null) {
            SectionLabel(stringResource(R.string.section_games))
            return@Box
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                item.game.title,
                color = DaijiColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(shelfDetail(item), color = DaijiColors.Muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun shelfDetail(item: ShelfGame): String {
    val system = Systems.byId(item.game.systemId)?.shortName
    val played = item.lastPlayed?.let {
        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, SHORT_DATE).toString()
    }
    val hint = when (item.kind) {
        ShelfKind.RANDOM -> stringResource(R.string.hint_reroll)
        ShelfKind.FAVOURITE -> stringResource(R.string.hint_unfavourite)
        ShelfKind.RECENT -> null
    }
    return listOfNotNull(system, played, hint).joinToString("  ·  ")
}

/** Games in the dock's band and at its tile size, ending with the way into the whole library. */
@Composable
private fun GamesShelf(
    items: List<ShelfGame>,
    gameCount: Int,
    memory: FocusMemory,
    focused: MutableState<HomeFocus?>,
    onPlay: (Game) -> Unit,
    onToggleFavourite: (Game) -> Unit,
    onReroll: () -> Unit,
    onOpenAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DaijiColors.DockBand)
            .horizontalScroll(rememberScrollState())
            .padding(BAND_PADDING),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items.forEach { item ->
            GameTile(
                game = item.game,
                onClick = { onPlay(item.game) },
                onLongClick = { if (item.kind == ShelfKind.RANDOM) onReroll() else onToggleFavourite(item.game) },
                modifier = memory.tag(shelfKey(item))
                    .onFocusChanged { if (it.isFocused) focused.value = HomeFocus.Game(item) }
                    .size(DockTileWidth, DockTileHeight),
                landscape = true,
                titleSize = 13.sp,
                marker = item.kind.marker,
            )
        }
        FocusCard(
            onClick = onOpenAll,
            modifier = memory.tag("entry:games")
                .onFocusChanged { if (it.isFocused) focused.value = null }
                .size(DockTileWidth, DockTileHeight),
            container = DaijiColors.Accent.copy(alpha = 0.22f),
            focusedContainer = DaijiColors.Accent.copy(alpha = 0.4f),
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.all_games_entry), color = DaijiColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(NumberFormat.getIntegerInstance().format(gameCount), color = DaijiColors.Label, fontSize = 14.sp)
            }
        }
    }
}
