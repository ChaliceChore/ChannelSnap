package dev.chalicechore.channelsnap.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chalicechore.channelsnap.repository.BackupRepository
import dev.chalicechore.channelsnap.ui.components.MessageState
import dev.chalicechore.channelsnap.ui.components.SkeletonListLoading
import dev.chalicechore.channelsnap.ui.components.SpotIllustration
import dev.chalicechore.channelsnap.ui.components.channelGroups

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    snackbar: SnackbarHostState,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveAs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_JSON),
        vm::onSaveLocationPicked,
    )

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is HomeEffect.PickSaveLocation -> saveAs.launch(effect.suggestedName)
                is HomeEffect.Message -> {
                    val result = snackbar.showSnackbar(
                        message = effect.text,
                        actionLabel = if (effect.share != null) "Share" else null,
                        withDismissAction = effect.share == null,
                        duration = if (effect.share != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed && effect.share != null) {
                        shareBackup(context, effect.share)
                    }
                }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("ChannelSnap") },
            actions = { RootStatusChip(state.rootStatus, onClick = vm::checkRoot) },
        )

        ActionButtons(state, onScan = vm::scan, onBackUp = vm::backUp, onRestore = onRestore)

        AnimatedContent(
            targetState = state.scan,
            contentKey = { it.javaClass },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "scan-state",
        ) { scan ->
            when (scan) {
                ScanState.Idle -> MessageState(
                    title = "Snapshot your silenced channels",
                    body = "Scan to find every notification channel you've turned off across your apps, " +
                        "then save them to a backup you can restore after a reset or on a new ROM.",
                    illustration = { SpotIllustration(Icons.Rounded.NotificationsOff, Icons.Rounded.Radar) },
                    actions = {
                        Button(onClick = vm::scan) {
                            Icon(Icons.Rounded.Radar, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Scan now")
                        }
                    },
                )

                is ScanState.Scanning -> SkeletonListLoading(scan.step)

                is ScanState.Error -> MessageState(
                    title = if (scan.rootMissing) "Root access needed" else "Something went wrong",
                    body = scan.message,
                    illustration = {
                        SpotIllustration(
                            icon = if (scan.rootMissing) Icons.Rounded.Lock else Icons.Rounded.ErrorOutline,
                            badge = Icons.Rounded.WarningAmber,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                    actions = { Button(onClick = vm::scan) { Text("Try again") } },
                )

                is ScanState.Loaded -> if (scan.channels.isEmpty()) {
                    MessageState(
                        title = "No blocked channels",
                        body = "Every notification channel on this device is switched on, so there's " +
                            "nothing to back up yet. Turn off the channels you don't want in Settings → " +
                            "Notifications, then scan again.",
                        illustration = {
                            SpotIllustration(Icons.Rounded.NotificationsActive, Icons.Rounded.CheckCircle)
                        },
                        actions = { OutlinedButton(onClick = vm::scan) { Text("Scan again") } },
                    )
                } else {
                    ChannelResults(state, vm)
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(state: HomeUiState, onScan: () -> Unit, onBackUp: () -> Unit, onRestore: () -> Unit) {
    val scanned = state.scan is ScanState.Loaded
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onScan,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            ButtonLabel(Icons.Rounded.Radar, if (scanned) "Rescan" else "Scan")
        }
        Button(
            onClick = onBackUp,
            enabled = !state.busy && state.selected.isNotEmpty(),
            modifier = Modifier.weight(1f),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Saving")
            } else {
                ButtonLabel(Icons.Rounded.Save, "Back up")
            }
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            ButtonLabel(Icons.Rounded.Restore, "Restore")
        }
    }
}

@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(text, maxLines = 1)
}

@Composable
private fun ChannelResults(state: HomeUiState, vm: HomeViewModel) {
    val groups = state.visibleGroups
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search apps or channels") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        BatchActions(
            summary = "${state.selected.size} of ${state.channels.size} selected · ${state.channels.groupBy { it.packageName }.size} apps",
            enabled = !state.busy && groups.isNotEmpty(),
            onAll = vm::selectAll,
            onNone = vm::selectNone,
            onInvert = vm::invertSelection,
            onCollapse = vm::toggleCollapseAll,
        )
        HorizontalDivider()

        if (groups.isEmpty()) {
            MessageState(
                title = "No matches",
                body = "Nothing matches “${state.query}”.",
                illustration = { SpotIllustration(Icons.Rounded.SearchOff, null) },
                actions = { OutlinedButton(onClick = { vm.setQuery("") }) { Text("Clear search") } },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                channelGroups(
                    groups = groups,
                    selected = state.selected,
                    collapsed = state.collapsed,
                    onToggleChannel = vm::toggle,
                    onToggleGroup = vm::toggleGroup,
                    onToggleCollapsed = vm::toggleCollapsed,
                    enabled = !state.busy,
                )
            }
        }
    }
}

/** The row of selection/batch actions above a channel list. Shared with the Restore screen. */
@Composable
fun BatchActions(
    summary: String,
    enabled: Boolean,
    onAll: () -> Unit,
    onNone: () -> Unit,
    onInvert: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(
            summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatchChip("All", Icons.Rounded.DoneAll, enabled, onAll)
            BatchChip("None", Icons.Rounded.RemoveDone, enabled, onNone)
            BatchChip("Invert", Icons.Rounded.SwapHoriz, enabled, onInvert)
            BatchChip("Collapse / expand", Icons.Rounded.UnfoldLess, enabled, onCollapse)
        }
    }
}

@Composable
private fun BatchChip(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun RootStatusChip(status: RootStatus, onClick: () -> Unit) {
    val (label, color) = when (status) {
        RootStatus.CHECKING -> "Checking root" to MaterialTheme.colorScheme.onSurfaceVariant
        RootStatus.GRANTED -> "Root granted" to MaterialTheme.colorScheme.primary
        RootStatus.MISSING -> "No root" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = onClick,
        enabled = status != RootStatus.CHECKING,
        label = { Text(label) },
        leadingIcon = {
            when (status) {
                RootStatus.CHECKING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                RootStatus.GRANTED -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = color)
                RootStatus.MISSING -> Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp), tint = color)
            }
        },
        modifier = Modifier.padding(end = 8.dp),
    )
}

private fun shareBackup(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(BackupRepository.MIME_JSON)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share backup"))
}
