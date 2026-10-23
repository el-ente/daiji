package io.github.elente.daiji.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.BuildConfig
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.launch.Emulators
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasAccess by vm.hasStorageAccess.collectAsStateWithLifecycle()

    var editingPath by remember { mutableStateOf(false) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onResume()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Settings",
                color = DaijiColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item { SectionTitle("Library") }
        item {
            SettingRow(
                title = "Storage access",
                value = if (hasAccess) "Granted" else "Needed — select to grant",
                onClick = {
                    if (!hasAccess) requestStorage(context, vm) { legacyPermission.launch(it) }
                },
            )
        }
        item {
            SettingRow(
                title = "ROMs folder",
                value = settings.romRoot ?: "Not set",
                onClick = {
                    val candidates = vm.candidateRoots()
                    if (candidates.isEmpty()) {
                        vm.toast("No ROMs folder found. Create one called ROMs, or type a path below.")
                    } else {
                        val next = candidates[(candidates.indexOf(settings.romRoot) + 1) % candidates.size]
                        vm.setRomRoot(next)
                    }
                },
            )
        }
        item {
            if (editingPath) {
                PathEditor(
                    initial = settings.romRoot ?: "/storage/emulated/0/ROMs",
                    onSave = {
                        vm.setRomRoot(it)
                        editingPath = false
                    },
                )
            } else {
                SettingRow(title = "Type a folder path", value = "", onClick = { editingPath = true })
            }
        }
        item {
            SettingRow(
                title = "Rescan library",
                value = if (scanning) "Scanning…" else "${library.games.size} games",
                onClick = { vm.rescan() },
            )
        }

        item { SectionTitle("Emulators") }
        if (library.systems.isEmpty()) {
            item {
                Text(
                    "Systems show up here once games are found.",
                    color = DaijiColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        items(library.systems, key = { it.id }) { system ->
            val emulator = Emulators.resolve(system, settings)
            val installed = emulator.id == Emulators.ASK || emulator.id in settings.installedEmulators
            SettingRow(
                title = system.name,
                value = emulator.name + if (installed) "" else " (not installed)",
                onClick = { vm.cycleEmulator(system) },
            )
        }

        item { SectionTitle("Device") }
        item {
            SettingRow(
                title = "Android settings",
                value = "",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
        }
        item {
            Text(
                text = "To make Daiji your home screen on Google TV, disable the stock launcher over ADB:\n" +
                    "adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx\n" +
                    "adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith\n\n" +
                    "Daiji ${BuildConfig.VERSION_NAME}",
                color = DaijiColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = DaijiColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        focusedContainer = DaijiColors.SurfaceHigh,
        focusedScale = 1.01f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = DaijiColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = DaijiColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PathEditor(initial: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) {
            if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(32)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(DaijiColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = DaijiColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(DaijiColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field),
            )
        }
        PillButton(text = "Save", onClick = { onSave(text) }, primary = true)
    }
}

private fun requestStorage(context: Context, vm: MainViewModel, legacy: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
        val opened = intents.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) {
            vm.toast("Grant it over ADB: adb shell appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        }
    } else {
        legacy(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
