package io.github.elente.daiji.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.data.AppEntry

private val TileWidth = 150.dp
private val TileHeight = 84.dp

@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 42.dp, end = 42.dp, top = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Apps", color = DaijiColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Hold OK on an app to open its info page.",
            color = DaijiColors.Muted,
            fontSize = 12.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 32.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    onClick = { vm.launch(app) },
                    onLongClick = { vm.openAppInfo(app) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val density = LocalDensity.current
    val w = with(density) { TileWidth.roundToPx() }
    val h = with(density) { TileHeight.roundToPx() }
    val art = rememberAppArt(app, w, h)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            when {
                art == null -> Text(
                    text = app.label.take(1).uppercase(),
                    color = DaijiColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
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
                        .size(44.dp),
                )
            }
        }
        Text(
            text = app.label,
            color = DaijiColors.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
