package io.github.elente.daiji.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository
import io.github.elente.daiji.data.Game
import kotlinx.coroutines.delay
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
private val BandTileHeight = 118.dp
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
    val continueGame = remember(recents, library) {
        recents.entries.sortedByDescending { it.value }.firstNotNullOfOrNull { library.byPath[it.key] }
    }
    val favouriteGame = remember(favourites, library) {
        favourites.entries.sortedByDescending { it.value }.firstNotNullOfOrNull { library.byPath[it.key] }
    }
    val focusedDockApp = remember { mutableStateOf<AppEntry?>(null) }
    val memory = rememberFocusMemory { vm.focusMemory[Screen.HOME] = it }
    // Apps load a moment after Home appears. Restoring earlier would land on a placeholder, and remember it.
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        memory.restore(vm.focusMemory[Screen.HOME], dock.firstOrNull()?.let(::dockKey), "entry:games")
    }

    AmbientGlow(focusedDockApp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(network = network, memory = memory, onOpenSettings = { vm.open(Screen.SETTINGS) })
        DockTitle(focusedDockApp)
        Dock(
            apps = dock,
            appsLoaded = apps.isNotEmpty(),
            memory = memory,
            focusedApp = focusedDockApp,
            onLaunch = vm::launch,
            onTogglePin = vm::togglePin,
            onOpenApps = { vm.open(Screen.ALL_APPS) },
        )
        GamesBand(
            continueGame = continueGame,
            lastPlayed = continueGame?.let { recents[it.path] },
            favouriteGame = favouriteGame,
            randomGame = randomPick,
            memory = memory,
            onPlay = vm::launch,
            onToggleFavourite = vm::toggleFavourite,
            onReroll = vm::rerollRandom,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EntryTile("All games", library.games.size, memory.tag("entry:games")) { vm.open(Screen.ALL_GAMES) }
            EntryTile("All apps", apps.size, memory.tag("entry:apps")) { vm.open(Screen.ALL_APPS) }
        }
    }
}

private fun dockKey(app: AppEntry) = "dock:${app.packageName}"

@Composable
private fun Header(network: String, memory: FocusMemory, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Clock(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (network.isNotEmpty()) {
                GlassPill(text = network, icon = networkIcon(network), modifier = memory.tag("pill:network"), onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
            }
            GlassPill(text = "Settings", icon = Icons.Filled.Settings, modifier = memory.tag("pill:settings"), onClick = onOpenSettings)
        }
    }
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
        Text(greeting(now).uppercase(), color = DaijiColors.Muted, fontSize = 12.sp, letterSpacing = 3.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(time, color = DaijiColors.Text, fontSize = 52.sp, fontWeight = FontWeight.Thin, lineHeight = 54.sp)
            Text(
                date.uppercase(),
                color = DaijiColors.Muted,
                fontSize = 13.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

private fun greeting(now: Long): String = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..19 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * A glow in the colour of the focused dock app's banner, spread over the whole screen behind the app's name so it
 * has no edges. Reads the focus state itself, so only the glow recomposes when focus moves.
 */
@Composable
private fun AmbientGlow(focusedApp: MutableState<AppEntry?>) {
    val density = LocalDensity.current
    val tone = rememberAppTone(
        focusedApp.value,
        with(density) { AppArtWidth.roundToPx() },
        with(density) { AppArtHeight.roundToPx() },
    ) ?: return
    val center = with(density) { Offset(GlowCenterX.toPx(), GlowCenterY.toPx()) }
    val radius = with(density) { GlowRadius.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(tone.copy(alpha = GLOW_ALPHA), Color.Transparent), center, radius)),
    )
}

/** The focused dock app's name in large light type. */
@Composable
private fun DockTitle(focusedApp: MutableState<AppEntry?>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = focusedApp.value?.label.orEmpty(),
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
    focusedApp: MutableState<AppEntry?>,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onOpenApps: () -> Unit,
) {
    var actionsFor by remember { mutableStateOf<AppEntry?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DaijiColors.DockBand)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (appsLoaded && apps.isEmpty()) {
            EntryTile("Pin apps from All apps", null, memory.tag("dock:empty"), onClick = onOpenApps)
        }
        apps.forEach { app ->
            AppTile(
                app = app,
                onClick = { onLaunch(app) },
                onLongClick = { actionsFor = app },
                modifier = memory.tag(dockKey(app))
                    .onFocusChanged { if (it.isFocused) focusedApp.value = app }
                    .size(DockTileWidth, DockTileHeight),
            )
        }
    }
    actionsFor?.let { app ->
        AppActionsDialog(app = app, isPinned = true, onTogglePin = { onTogglePin(app) }, onDismiss = { actionsFor = null })
    }
}

@Composable
private fun GamesBand(
    continueGame: Game?,
    lastPlayed: Long?,
    favouriteGame: Game?,
    randomGame: Game?,
    memory: FocusMemory,
    onPlay: (Game) -> Unit,
    onToggleFavourite: (Game) -> Unit,
    onReroll: () -> Unit,
) {
    val playedLabel = lastPlayed?.let {
        "played " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, SHORT_DATE)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BandSlot("Continue", playedLabel, continueGame, "Games you play show up here", Modifier.weight(1f)) {
            GameTile(it, onClick = { onPlay(it) }, onLongClick = { onToggleFavourite(it) },
                modifier = memory.tag("game:continue").fillMaxSize(), landscape = true, titleSize = 18.sp)
        }
        BandSlot("Favourite", null, favouriteGame, "Hold OK on a game to make it a favourite", Modifier.weight(1f)) {
            GameTile(it, onClick = { onPlay(it) }, onLongClick = { onToggleFavourite(it) },
                modifier = memory.tag("game:favourite").fillMaxSize(), landscape = true, titleSize = 18.sp)
        }
        BandSlot("Random", "hold OK for another", randomGame, "Add games to see a random pick", Modifier.weight(1f)) {
            GameTile(it, onClick = { onPlay(it) }, onLongClick = onReroll,
                modifier = memory.tag("game:random").fillMaxSize(), landscape = true, titleSize = 18.sp)
        }
    }
}

/** A labelled 16:9 slot. With no game it shows a hint that cannot take focus, so D-pad moves skip it. */
@Composable
private fun BandSlot(
    label: String,
    detail: String?,
    game: Game?,
    emptyHint: String,
    modifier: Modifier,
    tile: @Composable (Game) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = listOfNotNull(label.uppercase(), detail).joinToString("  ·  "),
            color = DaijiColors.Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(BandTileHeight)) {
            if (game != null) {
                tile(game)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DaijiColors.DockBand)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emptyHint, color = DaijiColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EntryTile(title: String, count: Int?, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = modifier, container = DaijiColors.DockBand, focusedContainer = DaijiColors.Glass) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = DaijiColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (count != null) Text("%,d".format(count), color = DaijiColors.Muted, fontSize = 17.sp)
        }
    }
}
