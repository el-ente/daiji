package io.github.elente.daiji.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.BuildConfig
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.launch.Emulators
import java.io.File

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasAccess by vm.hasStorageAccess.collectAsStateWithLifecycle()

    var pickingFolder by remember { mutableStateOf(false) }
    var returnedFromPicker by remember { mutableStateOf(false) }
    val romsFolderRow = remember { FocusRequester() }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onResume()
    }

    if (pickingFolder) {
        FolderPicker(
            start = (settings.romRoot ?: vm.candidateRoots().firstOrNull())?.let(::File),
            onPick = {
                vm.setRomRoot(it.path)
                pickingFolder = false
                returnedFromPicker = true
            },
            onCancel = {
                pickingFolder = false
                returnedFromPicker = true
            },
        )
        return
    }
    if (returnedFromPicker) RequestFocusWhenReady(romsFolderRow)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.settings),
                color = DaijiColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item { SectionTitle(stringResource(R.string.section_library)) }
        item {
            SettingRow(
                title = stringResource(R.string.storage_access),
                value = stringResource(if (hasAccess) R.string.storage_granted else R.string.storage_needed),
                onClick = {
                    if (!hasAccess) requestStorage(context, vm) { legacyPermission.launch(it) }
                },
            )
        }
        item {
            SettingRow(
                title = stringResource(R.string.roms_folder),
                value = settings.romRoot ?: stringResource(R.string.not_set),
                onClick = { pickingFolder = true },
                modifier = Modifier.focusRequester(romsFolderRow),
            )
        }
        item {
            SettingRow(
                title = stringResource(R.string.rescan_library),
                value = if (scanning) stringResource(R.string.scanning) else pluralStringResource(R.plurals.game_count, library.games.size, library.games.size),
                onClick = { vm.rescan() },
            )
        }

        item { SectionTitle(stringResource(R.string.section_emulators)) }
        item {
            val setup by vm.setup.collectAsStateWithLifecycle()
            SettingRow(
                title = stringResource(R.string.set_up_retroarch),
                value = stringResource(if (setup.isComplete) R.string.setup_done else R.string.setup_summary),
                onClick = { vm.openSetup() },
            )
        }
        if (library.systems.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_systems),
                    color = DaijiColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        items(library.systems, key = { it.id }) { system ->
            val emulator = Emulators.resolve(system, settings)
            val installed = emulator.id == Emulators.ASK || emulator.id in settings.installedEmulators
            val name = if (emulator.id == Emulators.ASK) stringResource(R.string.emulator_ask_android) else emulator.name
            SettingRow(
                title = system.name,
                value = if (installed) name else stringResource(R.string.emulator_not_installed, name),
                onClick = { vm.cycleEmulator(system) },
            )
        }

        item { SectionTitle(stringResource(R.string.section_device)) }
        item {
            SettingRow(
                title = stringResource(R.string.android_settings),
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
                text = stringResource(R.string.home_screen_hint) + "\n" +
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
internal fun SettingRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
            vm.toast(context.getString(R.string.grant_over_adb, "adb shell appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"))
        }
    } else {
        legacy(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
