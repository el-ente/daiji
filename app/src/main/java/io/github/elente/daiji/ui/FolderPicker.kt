package io.github.elente.daiji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.elente.daiji.data.RomScanner
import java.io.File
import kotlinx.coroutines.delay

/** D-pad folder browser. `current == null` shows the storage volumes. */
@Composable
fun FolderPicker(start: File?, onPick: (File) -> Unit, onCancel: () -> Unit) {
    var current by remember { mutableStateOf(start?.takeIf { it.isDirectory }) }
    val volumes = remember { RomScanner.volumes() }
    val isVolume = current != null && volumes.any { it.path == current?.path }

    fun goUp() {
        current = if (isVolume) null else current?.parentFile
    }
    BackHandler { if (current == null) onCancel() else goUp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("ROMs folder", color = DaijiColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        PickerHeader(current)
        val dir = current
        if (dir == null) {
            VolumeList(volumes) { current = it }
        } else {
            SubfolderList(dir, onPick = { onPick(dir) }, onUp = ::goUp, onOpen = { current = it })
        }
    }
}

@Composable
private fun PickerHeader(current: File?) {
    Text(
        text = current?.path ?: "Choose a storage volume",
        color = DaijiColors.Muted,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun VolumeList(volumes: List<File>, onOpen: (File) -> Unit) {
    val first = remember { FocusRequester() }
    RequestFocusWhenReady(first)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(volumes, key = { it.path }) { volume ->
            val modifier = if (volume == volumes.first()) Modifier.focusRequester(first) else Modifier
            SettingRow(title = volumeLabel(volume), value = volume.path, onClick = { onOpen(volume) }, modifier = modifier)
        }
    }
}

@Composable
private fun SubfolderList(dir: File, onPick: () -> Unit, onUp: () -> Unit, onOpen: (File) -> Unit) {
    val children = remember(dir) { subfolders(dir) }
    val first = remember(dir) { FocusRequester() }
    RequestFocusWhenReady(first, key = dir)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            UseFolderRow(dir, onClick = onPick, modifier = Modifier.focusRequester(first))
        }
        item { SettingRow(title = "..", value = "Up", onClick = onUp) }
        if (children == null) {
            item { Hint("Can't read this folder. Check storage access.") }
        } else if (children.isEmpty()) {
            item { Hint("No subfolders") }
        }
        items(children.orEmpty(), key = { it.path }) { child ->
            SettingRow(title = child.name, value = "", onClick = { onOpen(child) })
        }
    }
}

@Composable
private fun UseFolderRow(dir: File, onClick: () -> Unit, modifier: Modifier) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        container = DaijiColors.Accent,
        focusedContainer = DaijiColors.AccentFocused,
        focusedScale = 1.01f,
    ) {
        Text(
            text = "Use this folder: ${dir.name}",
            color = DaijiColors.OnAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DaijiColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
internal fun RequestFocusWhenReady(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        repeat(5) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(32)
        }
    }
}

private fun subfolders(dir: File): List<File>? =
    dir.listFiles()
        ?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }

private fun volumeLabel(volume: File): String =
    if (volume.path.startsWith("/storage/emulated")) "Internal storage" else volume.name
