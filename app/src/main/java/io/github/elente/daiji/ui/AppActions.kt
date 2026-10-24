package io.github.elente.daiji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.Screen
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository

/** Hold OK or Menu on an app: pin it to Home or unpin it, or open its info page. */
@Composable
fun AppActionsDialog(app: AppEntry, isPinned: Boolean, onTogglePin: () -> Unit, onDismiss: () -> Unit) {
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
            Text(app.label, color = DaijiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            PillButton(
                text = stringResource(if (isPinned) R.string.unpin_from_home else R.string.pin_to_home),
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
                onClick = {
                    onTogglePin()
                    onDismiss()
                },
            )
            PillButton(text = stringResource(R.string.app_info), modifier = Modifier.fillMaxWidth(), onClick = {
                AppsRepository.openInfo(context, app)
                onDismiss()
            })
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
