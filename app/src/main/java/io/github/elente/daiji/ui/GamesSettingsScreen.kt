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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.GameSystem
import io.github.elente.daiji.data.SettingsSnapshot
import io.github.elente.daiji.launch.Emulators
import io.github.elente.daiji.setup.SetupState
import java.io.File

private const val ROW_STORAGE = "storage"
private const val ROW_ROMS = "roms"
private const val ROW_RESCAN = "rescan"
private const val ROW_RETROARCH = "retroarch"
private const val ROW_EMULATOR = "emu:"

@Composable
fun GamesSettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasAccess by vm.hasStorageAccess.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()

    var pickingFolder by remember { mutableStateOf(false) }
    val memory = rememberFocusMemory { vm.focusMemory[Screen.GAMES_SETTINGS] = it }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onResume()
    }

    if (pickingFolder) {
        FolderPicker(
            start = (settings.romRoot ?: vm.candidateRoots().firstOrNull())?.let(::File),
            onPick = {
                vm.setRomRoot(it.path)
                pickingFolder = false
            },
            onCancel = { pickingFolder = false },
        )
        return
    }
    LaunchedEffect(pickingFolder) { memory.restore(vm.focusMemory[Screen.GAMES_SETTINGS], ROW_STORAGE, ROW_ROMS) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SettingsTitle(stringResource(R.string.games_settings)) }

        item { SectionTitle(stringResource(R.string.section_library)) }
        if (!hasAccess) {
            item {
                SettingRow(
                    title = stringResource(R.string.storage_access),
                    value = stringResource(R.string.storage_needed),
                    onClick = { requestStorage(context, vm) { legacyPermission.launch(it) } },
                    modifier = memory.tag(ROW_STORAGE),
                )
            }
        }
        item {
            SettingRow(
                title = stringResource(R.string.roms_folder),
                value = settings.romRoot ?: stringResource(R.string.not_set),
                onClick = { pickingFolder = true },
                modifier = memory.tag(ROW_ROMS),
            )
        }
        item {
            SettingRow(
                title = stringResource(R.string.rescan_library),
                value = if (scanning) stringResource(R.string.scanning) else pluralStringResource(R.plurals.game_count, library.games.size, library.games.size),
                onClick = { vm.rescan() },
                modifier = memory.tag(ROW_RESCAN),
            )
        }

        item { SectionTitle(stringResource(R.string.section_emulators)) }
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
            ChoiceRow(
                title = system.name,
                value = emulatorLabel(system, settings),
                onStep = { vm.cycleEmulator(system, it) },
                modifier = memory.tag(ROW_EMULATOR + system.id),
            )
        }
        item {
            SettingRow(
                title = stringResource(R.string.retroarch),
                value = retroArchLabel(setup),
                onClick = { vm.openSetup() },
                modifier = memory.tag(ROW_RETROARCH),
            )
        }
    }
}

@Composable
private fun emulatorLabel(system: GameSystem, settings: SettingsSnapshot): String {
    val emulator = Emulators.resolve(system, settings)
    if (emulator.id == Emulators.ASK) return stringResource(R.string.emulator_ask_android)
    if (emulator.id in settings.installedEmulators) return emulator.name
    return stringResource(R.string.emulator_not_installed, emulator.name)
}

@Composable
private fun retroArchLabel(setup: SetupState): String = when {
    setup.isComplete -> stringResource(R.string.retroarch_installed)
    setup.isInstalled -> stringResource(R.string.setup_summary)
    else -> stringResource(R.string.retroarch_not_installed_set_up)
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
