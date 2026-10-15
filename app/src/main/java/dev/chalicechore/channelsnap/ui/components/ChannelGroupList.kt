package dev.chalicechore.channelsnap.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chalicechore.channelsnap.model.ChannelBackupEntry

/** Blocked channels of one app, for the grouped list. */
data class AppGroup(
    val packageName: String,
    val appLabel: String,
    val channels: List<ChannelBackupEntry>,
)

/** Groups channels by app, keeping the parser's sort order (app label, then channel name). */
fun List<ChannelBackupEntry>.groupByApp(): List<AppGroup> =
    groupBy { it.packageName }.map { (pkg, list) -> AppGroup(pkg, list.first().appLabel, list) }

fun AppGroup.toggleState(selected: Set<String>): ToggleableState {
    val n = channels.count { it.key in selected }
    return when (n) {
        0 -> ToggleableState.Off
        channels.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

/**
 * Adds a collapsible, selectable app → channels list to a LazyColumn. Shared by the
 * Home (scan results) and Restore (backup contents) screens.
 *
 * @param groupBadge optional trailing content on the app header (e.g. "Not installed").
 */
fun LazyListScope.channelGroups(
    groups: List<AppGroup>,
    selected: Set<String>,
    collapsed: Set<String>,
    onToggleChannel: (String) -> Unit,
    onToggleGroup: (AppGroup) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    enabled: Boolean = true,
    groupBadge: (@Composable (AppGroup) -> Unit)? = null,
) {
    groups.forEach { group ->
        val isCollapsed = group.packageName in collapsed
        item(key = "h:${group.packageName}", contentType = "header") {
            AppHeader(
                group = group,
                state = group.toggleState(selected),
                collapsed = isCollapsed,
                enabled = enabled,
                onToggle = { onToggleGroup(group) },
                onToggleCollapsed = { onToggleCollapsed(group.packageName) },
                badge = groupBadge?.let { { it(group) } },
            )
        }
        if (!isCollapsed) {
            items(group.channels, key = { "c:${it.key}" }, contentType = { "channel" }) { channel ->
                ChannelRow(
                    channel = channel,
                    checked = channel.key in selected,
                    enabled = enabled,
                    onToggle = { onToggleChannel(channel.key) },
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    group: AppGroup,
    state: ToggleableState,
    collapsed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onToggleCollapsed: () -> Unit,
    badge: (@Composable () -> Unit)?,
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleCollapsed)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(group.packageName, group.appLabel)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${group.channels.size} channel${if (group.channels.size == 1) "" else "s"} · ${group.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    badge?.invoke()
                }
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
            TriStateCheckbox(state = state, onClick = onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun ChannelRow(channel: ChannelBackupEntry, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(start = 70.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(channel.channelName, style = MaterialTheme.typography.bodyMedium)
            if (channel.channelName != channel.channelId) {
                Text(
                    channel.channelId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(14.dp))
    }
}
