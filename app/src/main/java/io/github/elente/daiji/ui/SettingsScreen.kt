package io.github.elente.daiji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.elente.daiji.BuildConfig
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.Screen

private const val ROW_GAMES = "games"
private const val ROW_ANDROID = "android"

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val memory = rememberFocusMemory { vm.focusMemory[Screen.SETTINGS] = it }
    LaunchedEffect(Unit) { memory.restore(vm.focusMemory[Screen.SETTINGS], ROW_GAMES) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SettingsTitle(stringResource(R.string.settings)) }
        item {
            SettingRow(
                title = stringResource(R.string.section_games),
                opensScreen = true,
                onClick = { vm.open(Screen.GAMES_SETTINGS) },
                modifier = memory.tag(ROW_GAMES),
            )
        }
        item {
            SettingRow(
                title = stringResource(R.string.android_settings),
                onClick = { openAndroidSettings(context) },
                modifier = memory.tag(ROW_ANDROID),
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
internal fun SettingsTitle(text: String) {
    Text(
        text,
        color = DaijiColors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
