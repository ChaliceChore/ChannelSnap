package dev.chalicechore.channelsnap.ui.screens.restore

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chalicechore.channelsnap.model.BackupFile
import dev.chalicechore.channelsnap.model.ChannelRestoreResult
import dev.chalicechore.channelsnap.model.RestoreOutcome
import dev.chalicechore.channelsnap.model.RestoreResult
import dev.chalicechore.channelsnap.ui.components.AppIcon
import dev.chalicechore.channelsnap.ui.components.MessageState
import dev.chalicechore.channelsnap.ui.components.SpotIllustration
import dev.chalicechore.channelsnap.ui.components.channelGroups
import dev.chalicechore.channelsnap.ui.screens.home.BatchActions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(vm: RestoreViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val phase = state.phase
    val busy = phase is RestorePhase.Restoring

    // Leaving mid-restore is harmless (the root session finishes regardless), but the
    // result would be lost, so back is ignored until it completes.
    BackHandler(enabled = busy) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (phase is RestorePhase.Done) "Restore complete" else "Restore backup") },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            when (phase) {
                is RestorePhase.Ready -> BottomAction {
                    Button(
                        onClick = vm::restore,
                        enabled = state.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        val n = state.selected.size
                        Text(if (n == 0) "Select channels to restore" else "Block $n channel${if (n == 1) "" else "s"}")
                    }
                }
                is RestorePhase.Done -> BottomAction {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = vm::backToSelection, modifier = Modifier.weight(1f)) { Text("Restore again") }
                        Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Done") }
                    }
                }
                else -> {}
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = phase,
            contentKey = { it.javaClass },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
            label = "restore-phase",
        ) { p ->
            when (p) {
                RestorePhase.Loading -> CenteredProgress("Reading backup…")
                is RestorePhase.Restoring -> CenteredProgress(
                    "Blocking ${p.count} channel${if (p.count == 1) "" else "s"}…\nApprove the root prompt if one appears.",
                )
                is RestorePhase.Error -> MessageState(
                    title = if (p.rootMissing) "Root access needed" else "Couldn't restore",
                    body = p.message,
                    illustration = {
                        SpotIllustration(
                            icon = if (p.rootMissing) Icons.Rounded.Lock else Icons.Rounded.ErrorOutline,
                            badge = Icons.Rounded.WarningAmber,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                    actions = {
                        OutlinedButton(onClick = onClose) { Text("Close") }
                        if (state.backup != null) Button(onClick = vm::backToSelection) { Text("Try again") }
                    },
                )
                is RestorePhase.Ready -> SelectionList(p.backup, state, vm)
                is RestorePhase.Done -> ResultList(p.result)
            }
        }
    }
}

@Composable
private fun BottomAction(content: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { content() }
    }
}

@Composable
private fun CenteredProgress(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SelectionList(backup: BackupFile, state: RestoreUiState, vm: RestoreViewModel) {
    val total = state.groups.sumOf { it.channels.size }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "summary") { BackupSummary(backup, total, state.notInstalled.size) }
        item(key = "batch") {
            BatchActions(
                summary = "${state.selected.size} of $total selected",
                enabled = true,
                onAll = vm::selectAll,
                onNone = vm::selectNone,
                onInvert = vm::invertSelection,
                onCollapse = vm::toggleCollapseAll,
            )
            HorizontalDivider(Modifier.padding(top = 4.dp))
        }
        channelGroups(
            groups = state.groups,
            selected = state.selected,
            collapsed = state.collapsed,
            onToggleChannel = vm::toggle,
            onToggleGroup = vm::toggleGroup,
            onToggleCollapsed = vm::toggleCollapsed,
            groupBadge = { group ->
                if (group.packageName in state.notInstalled) {
                    Text(
                        "Not installed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun BackupSummary(backup: BackupFile, total: Int, missingApps: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Restore, null, Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("$total blocked channel${if (total == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                val device = listOfNotNull(
                    backup.deviceModel.ifBlank { null },
                    backup.androidVersion.ifBlank { null }?.let { "Android $it" },
                ).joinToString(" · ")
                if (device.isNotEmpty()) Text(device, style = MaterialTheme.typography.bodyMedium)
                formatTimestamp(backup.backupTimestamp)?.let {
                    Text("Saved $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (missingApps > 0) {
            Text(
                "$missingApps app${if (missingApps == 1) " isn't" else "s aren't"} installed on this device and " +
                    "will be skipped. Install them, then restore again.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

private fun formatTimestamp(iso: String): String? = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
}.getOrNull()

// ── Results ────────────────────────────────────────────────────────────────

private data class OutcomeStyle(val label: String, val icon: ImageVector)

private fun RestoreOutcome.style() = when (this) {
    RestoreOutcome.BLOCKED -> OutcomeStyle("Blocked", Icons.Rounded.CheckCircle)
    RestoreOutcome.ALREADY_BLOCKED -> OutcomeStyle("Already blocked", Icons.Rounded.Block)
    RestoreOutcome.CHANNEL_MISSING -> OutcomeStyle("Open app first", Icons.Rounded.HourglassEmpty)
    RestoreOutcome.APP_MISSING -> OutcomeStyle("Not installed", Icons.Rounded.RemoveCircleOutline)
    RestoreOutcome.FAILED -> OutcomeStyle("Failed", Icons.Rounded.ErrorOutline)
}

@Composable
private fun RestoreOutcome.tint(): Color = when (this) {
    RestoreOutcome.BLOCKED, RestoreOutcome.ALREADY_BLOCKED -> MaterialTheme.colorScheme.primary
    RestoreOutcome.CHANNEL_MISSING -> MaterialTheme.colorScheme.tertiary
    RestoreOutcome.APP_MISSING -> MaterialTheme.colorScheme.onSurfaceVariant
    RestoreOutcome.FAILED -> MaterialTheme.colorScheme.error
}

/** Problems first, so anything needing action is at the top. */
private val outcomeOrder = listOf(
    RestoreOutcome.FAILED, RestoreOutcome.CHANNEL_MISSING, RestoreOutcome.APP_MISSING,
    RestoreOutcome.BLOCKED, RestoreOutcome.ALREADY_BLOCKED,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultList(result: RestoreResult) {
    val total = result.channels.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "headline") {
            Text(
                "${result.succeeded} of $total channel${if (total == 1) "" else "s"} blocked",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                outcomeOrder.forEach { o ->
                    val n = result.count(o)
                    if (n > 0) StatTile(n, o)
                }
            }
        }
        if (result.count(RestoreOutcome.CHANNEL_MISSING) > 0) {
            item(key = "hint") {
                Hint(
                    "Apps create their notification channels the first time they run. Open the apps marked " +
                        "“Open app first” once, then come back and tap Restore again.",
                )
            }
        }
        if (result.count(RestoreOutcome.FAILED) > 0) {
            item(key = "hint-failed") {
                Hint("Some channels couldn't be blocked. The reason is shown under each one.")
            }
        }
        outcomeOrder.forEach { o ->
            val rows = result.channels.filter { it.outcome == o }
            if (rows.isNotEmpty()) {
                item(key = "sec:$o") {
                    Text(
                        o.style().label,
                        style = MaterialTheme.typography.titleSmall,
                        color = o.tint(),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { "r:${it.entry.key}" }) { ResultRow(it) }
            }
        }
    }
}

@Composable
private fun StatTile(count: Int, outcome: RestoreOutcome) {
    val tint = outcome.tint()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(outcome.style().icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.titleMedium, color = tint)
            Spacer(Modifier.width(6.dp))
            Text(outcome.style().label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun ResultRow(r: ChannelRestoreResult) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(r.entry.packageName, r.entry.appLabel, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.entry.channelName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                r.entry.appLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            r.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Icon(r.outcome.style().icon, contentDescription = r.outcome.style().label, tint = r.outcome.tint())
    }
}
