package io.github.elente.daiji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R
import io.github.elente.daiji.setup.SetupState
import io.github.elente.daiji.setup.SetupStep
import io.github.elente.daiji.setup.Step
import io.github.elente.daiji.setup.StepStatus

@Composable
fun SetupScreen(vm: MainViewModel) {
    val setup by vm.setup.collectAsStateWithLifecycle()
    val primaryButton = remember { FocusRequester() }

    BackHandler { vm.closeSetup() }
    RequestFocusWhenReady(primaryButton)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.set_up_retroarch), color = DaijiColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.setup_intro),
                color = DaijiColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
        }
        items(setup.steps.size) { StepRow(setup.steps[it]) }
        item { Actions(setup, vm, Modifier.padding(top = 16.dp), primaryButton) }
    }
}

@Composable
private fun StepRow(step: SetupStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(statusMark(step.status), color = statusColor(step.status), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(step.step.title), color = DaijiColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(step.detail, color = statusColor(step.status), fontSize = 13.sp)
    }
}

@Composable
private fun Actions(setup: SetupState, vm: MainViewModel, modifier: Modifier, primaryButton: FocusRequester) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            setup.busy -> Text(stringResource(R.string.setup_working), color = DaijiColors.Muted, fontSize = 13.sp)
            !setup.isInstalled -> PillButton(stringResource(R.string.setup_install), vm::runSetup, Modifier.focusRequester(primaryButton), primary = true)
            !setup.isProvisioned -> PillButton(stringResource(R.string.setup_provision), vm::runSetup, Modifier.focusRequester(primaryButton), primary = true)
            !setup.isComplete -> ImportCores(vm, primaryButton)
            else -> PillButton(stringResource(R.string.setup_done), vm::closeSetup, Modifier.focusRequester(primaryButton), primary = true)
        }
        if (setup.isProvisioned && setup[Step.THUMBNAILS].status != StepStatus.RUNNING) {
            PillButton(stringResource(R.string.setup_run_again), vm::runSetup)
        }
    }
}

@Composable
private fun ImportCores(vm: MainViewModel, primaryButton: FocusRequester) {
    Text(
        stringResource(R.string.setup_import_instructions),
        color = DaijiColors.Text,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(stringResource(R.string.open_retroarch), vm::openRetroArch, Modifier.focusRequester(primaryButton), primary = true)
        PillButton(stringResource(R.string.cores_imported), vm::markCoresImported)
    }
}

private fun statusMark(status: StepStatus): String = when (status) {
    StepStatus.PENDING -> "○"
    StepStatus.RUNNING -> "◌"
    StepStatus.DONE -> "●"
    StepStatus.FAILED -> "✕"
}

private fun statusColor(status: StepStatus): Color = when (status) {
    StepStatus.DONE -> DaijiColors.Accent
    StepStatus.FAILED -> Color(0xFFE57373)
    else -> DaijiColors.Muted
}
