package io.github.elente.daiji.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import io.github.elente.daiji.R

fun openAndroidSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Hold OK or Menu on the Home gear: Android settings or Daiji's own. */
@Composable
fun SettingsMenuDialog(onOpenLauncherSettings: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .ignoreHeldSelect()
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DaijiColors.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings), color = DaijiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            PillButton(
                text = stringResource(R.string.android_settings),
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
                onClick = {
                    openAndroidSettings(context)
                    onDismiss()
                },
            )
            PillButton(text = stringResource(R.string.launcher_settings), modifier = Modifier.fillMaxWidth(), onClick = {
                onOpenLauncherSettings()
                onDismiss()
            })
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
