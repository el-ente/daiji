package io.github.elente.daiji.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
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
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.Systems

private val CoverWidth = 240.dp
private val CoverHeight = 320.dp
private val TextColumnWidth = 520.dp
private val FieldLabelWidth = 120.dp

/** Dark under the text column, which ends past the middle of the screen, then lighter so the screenshot shows. */
private val BackdropFade = arrayOf(
    0f to DaijiColors.Background,
    0.6f to DaijiColors.Scrim,
    1f to DaijiColors.Background.copy(alpha = 0.55f),
)

/** Hold OK on a game in All games or Search: its art and RetroArch database info, with Play and Favourite. */
@Composable
fun GameDetailScreen(vm: MainViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val game = vm.detailGamePath?.let { library.byPath[it] }
    if (game == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val play = remember { FocusRequester() }

    // The hold that opened this page ends with a release of OK, which would otherwise launch the game.
    Box(Modifier.fillMaxSize().ignoreHeldSelect()) {
        Backdrop(game)
        Row(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            DetailArt(game)
            Column(Modifier.widthIn(max = TextColumnWidth), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GameInfo(game)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton(
                        text = stringResource(R.string.play),
                        primary = true,
                        modifier = Modifier.focusRequester(play),
                        onClick = { vm.launch(game) },
                    )
                    PillButton(
                        text = stringResource(if (game.path in favourites) R.string.is_favourite else R.string.add_to_favourites),
                        onClick = { vm.toggleFavourite(game) },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
}

/** The screenshot fills the screen behind a fade that keeps the text on the left readable. */
@Composable
private fun Backdrop(game: Game) {
    BoxWithConstraints(Modifier.fillMaxSize().background(DaijiColors.systemTone(game.systemId))) {
        rememberArt(game.screenshot, maxWidth, maxHeight)?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(*BackdropFade)))
    }
}

/** Box art, else the screenshot in landscape, else the system's tone with its badge. */
@Composable
private fun DetailArt(game: Game) {
    val isPortrait = game.cover != null
    val width = if (isPortrait || game.screenshot == null) CoverWidth else CoverHeight
    val height = if (isPortrait || game.screenshot == null) CoverHeight else CoverWidth
    val art = rememberArt(game.cover ?: game.screenshot, width, height)
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(10.dp))
            .background(DaijiColors.systemTone(game.systemId)),
    ) {
        if (art != null) {
            Image(bitmap = art, contentDescription = game.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Systems.byId(game.systemId)?.let { Badge(it.shortName, Modifier.align(Alignment.TopStart).padding(10.dp)) }
        }
    }
}

@Composable
private fun GameInfo(game: Game) {
    val info = game.info
    Systems.byId(game.systemId)?.let { system ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Badge(system.shortName)
            Text(system.name, color = DaijiColors.Muted, fontSize = 14.sp)
        }
    }
    Text(
        text = game.title,
        color = DaijiColors.Text,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val players = info.players?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.players, it, it) }
    val meta = listOfNotNull(info.year?.toString(), players, info.genre).joinToString("  ·  ")
    if (meta.isNotEmpty()) Text(meta, color = DaijiColors.Text, fontSize = 18.sp)
    info.developer?.let { InfoField(stringResource(R.string.developer), it) }
    info.publisher?.takeIf { it != info.developer }?.let { InfoField(stringResource(R.string.publisher), it) }
}

@Composable
private fun InfoField(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DaijiColors.Label, fontSize = 14.sp, modifier = Modifier.width(FieldLabelWidth))
        Text(value, color = DaijiColors.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rememberArt(path: String?, width: Dp, height: Dp) = with(LocalDensity.current) {
    rememberFileImage(path, width.roundToPx(), height.roundToPx())
}
