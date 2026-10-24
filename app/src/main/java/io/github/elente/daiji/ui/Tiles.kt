package io.github.elente.daiji.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.Game
import io.github.elente.daiji.data.Systems

/** Banner art is decoded at this size everywhere, so the dock and the apps grid share one cached bitmap. */
val AppArtWidth = 240.dp
val AppArtHeight = 135.dp

/** An app's banner, or its icon centred on a plain card. */
@Composable
fun AppTile(app: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val art = rememberAppArt(
        app,
        with(density) { AppArtWidth.roundToPx() },
        with(density) { AppArtHeight.roundToPx() },
    )
    FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        when {
            art == null -> Text(
                text = app.label,
                color = DaijiColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(10.dp),
            )
            art.second -> Image(
                bitmap = art.first,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Image(
                bitmap = art.first,
                contentDescription = app.label,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
        }
    }
}

@Composable
fun AppLabel(app: AppEntry, modifier: Modifier = Modifier) {
    Text(
        text = app.label,
        color = DaijiColors.Label,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A game's art, or for the many games without any, a tile coloured by its system with a large title and the
 * system's badge, readable from the sofa. Landscape tiles prefer a screenshot, portrait ones the box art.
 */
@Composable
fun GameTile(
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    titleSize: TextUnit = 15.sp,
    marker: String? = null,
    showCaption: Boolean = landscape,
    artScale: ContentScale = ContentScale.Crop,
    loadArt: Boolean = true,
) {
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        container = DaijiColors.systemTone(game.systemId),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val artPath = when {
                !loadArt -> null
                landscape -> game.screenshot ?: game.cover
                else -> game.cover ?: game.screenshot
            }
            val art = rememberTileArt(artPath, maxWidth, maxHeight)
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = game.title,
                    contentScale = artScale,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showCaption) ArtCaption(game.title, titleSize, Modifier.align(Alignment.BottomStart))
            } else {
                Systems.byId(game.systemId)?.let {
                    Badge(it.shortName, Modifier.align(Alignment.TopStart).padding(10.dp))
                }
                Text(
                    text = game.title,
                    color = DaijiColors.Text,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.15f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                )
            }
            marker?.let { Marker(it, Modifier.align(Alignment.TopEnd).padding(8.dp)) }
        }
    }
}

/** The title over the bottom of a screenshot, on a fade so it stays readable over any picture. */
@Composable
private fun ArtCaption(title: String, size: TextUnit, modifier: Modifier) {
    Text(
        text = title,
        color = DaijiColors.Text,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, DaijiColors.Scrim)))
            .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** ▶ ★ 🎲 on a solid dark disc, so they stay visible over busy art. */
@Composable
private fun Marker(glyph: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(DaijiColors.Marker),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = DaijiColors.Text, fontSize = 14.sp)
    }
}

@Composable
private fun rememberTileArt(path: String?, width: Dp, height: Dp) = with(LocalDensity.current) {
    rememberFileImage(path, width.roundToPx(), height.roundToPx())
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = DaijiColors.Text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(DaijiColors.Glass)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
