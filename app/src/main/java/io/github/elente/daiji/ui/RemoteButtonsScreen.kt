package io.github.elente.daiji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.elente.daiji.MainViewModel
import io.github.elente.daiji.R

@Composable
fun RemoteButtonsScreen(@Suppress("UNUSED_PARAMETER") vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SettingsTitle(stringResource(R.string.remote_buttons)) }
        item { RemoteButtonsSection() }
    }
}
