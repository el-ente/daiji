package io.github.elente.daiji.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import io.github.elente.daiji.remote.ButtonLearning
import io.github.elente.daiji.remote.ButtonMapping
import io.github.elente.daiji.remote.RemoteActions
import io.github.elente.daiji.remote.RemoteButtons
import io.github.elente.daiji.remote.RemoteKeyService

/** Which button is being set, and whether for a tap or a hold. */
private data class Editing(val scancode: Int, val hold: Boolean)

/** Settings > Remote buttons: the key service's state, the buttons set so far, and adding one by pressing it. */
@Composable
fun RemoteButtonsSection() {
    val context = LocalContext.current
    var mappings by remember { mutableStateOf(RemoteButtons.all(context)) }
    var learning by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Editing?>(null) }
    var options by remember { mutableStateOf<ButtonMapping?>(null) }
    val refresh = { mappings = RemoteButtons.all(context) }
    val serviceOn = isKeyServiceOn(context)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(
            title = "Key service",
            value = if (serviceOn) "On" else "Off — turn on Daiji in Accessibility",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
        mappings.forEach { mapping ->
            SettingRow(
                title = "Button ${mapping.scancode}",
                value = describe(context, mapping),
                onClick = { options = mapping },
            )
        }
        SettingRow(
            title = "Add a button",
            value = "Press it on the remote",
            onClick = { if (serviceOn) learning = true else toast(context, "Turn on the key service first") },
        )
    }

    if (learning) {
        LearnButtonDialog(
            onButton = { scancode ->
                learning = false
                picking = Editing(scancode, hold = false)
            },
            onDismiss = { learning = false },
        )
    }
    picking?.let { editing ->
        TargetPickerDialog(
            title = "Button ${editing.scancode}" + if (editing.hold) ", held" else "",
            onPick = { target ->
                RemoteButtons.set(context, editing.scancode, editing.hold, target)
                picking = null
                refresh()
            },
            onDismiss = { picking = null },
        )
    }
    options?.let { mapping ->
        ChoiceDialog(
            title = "Button ${mapping.scancode}",
            choices = listOfNotNull(
                "Change tap" to { picking = Editing(mapping.scancode, hold = false) },
                (if (mapping.hold == null) "Set a hold action" else "Change hold") to { picking = Editing(mapping.scancode, hold = true) },
                mapping.hold?.let { "Remove hold" to { RemoteButtons.clear(context, mapping.scancode, hold = true); refresh() } },
                "Remove button" to { RemoteButtons.remove(context, mapping.scancode); refresh() },
            ),
            onDismiss = { options = null },
        )
    }
}

private fun describe(context: Context, mapping: ButtonMapping): String = listOfNotNull(
    mapping.tap?.let { RemoteActions.label(context, it) },
    mapping.hold?.let { "hold: " + RemoteActions.label(context, it) },
).joinToString("  ·  ")

private fun isKeyServiceOn(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.name == RemoteKeyService::class.java.name }
}

private fun toast(context: Context, message: String) =
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()

@Composable
private fun LearnButtonDialog(onButton: (Int) -> Unit, onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        ButtonLearning.start(onButton)
        onDispose { ButtonLearning.stop() }
    }
    DialogPanel(onDismiss) {
        Text("Press a remote button", color = DaijiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "The arrows, OK, Back, Home and volume can't be set. Back cancels.",
            color = DaijiColors.Muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TargetPickerDialog(title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val targets = remember { RemoteActions.targets(context) }
    val first = remember { FocusRequester() }
    DialogPanel(onDismiss) {
        Text(title, color = DaijiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(targets, key = { it.id }) { target ->
                PillButton(
                    text = target.label,
                    onClick = { onPick(target.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (target == targets.first()) Modifier.focusRequester(first) else Modifier),
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun ChoiceDialog(title: String, choices: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    DialogPanel(onDismiss) {
        Text(title, color = DaijiColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        choices.forEachIndexed { index, (label, action) ->
            PillButton(
                text = label,
                onClick = {
                    onDismiss()
                    action()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
            )
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun DialogPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DaijiColors.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}
