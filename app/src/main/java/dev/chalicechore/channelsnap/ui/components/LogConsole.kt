package dev.chalicechore.channelsnap.ui.components

import androidx.compose.animation.AnimatedVisibility
import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val ConsoleBg = Color(0xFF0D1117)
private val ConsoleHeader = Color(0xFF161B22)
private val ConsoleText = Color(0xFF9BE9A8)
private val ConsoleDim = Color(0xFF6E7681)
private val ConsoleError = Color(0xFFFF7B72)
private val ConsoleRule = Color(0xFF79C0FF)

/**
 * Collapsible developer log. Only composed in debug builds (callers check
 * `BuildConfig.DEBUG`). Text isn't selectable on purpose, since selection steals the
 * scroll gesture; the Copy button covers that need.
 */
@Composable
fun LogConsole(lines: List<String>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size, expanded) {
        if (expanded && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Surface(color = ConsoleBg, contentColor = ConsoleText, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = ConsoleDim)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Debug log · ${lines.size} line${if (lines.size == 1) "" else "s"}",
                    color = ConsoleDim,
                    style = MonoStyle,
                    modifier = Modifier.weight(1f),
                )
                if (expanded) {
                    IconButton(onClick = {
                        val clip = ClipData.newPlainText("ChannelSnap log", lines.joinToString("\n"))
                        scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy log", tint = ConsoleDim)
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear log", tint = ConsoleDim)
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (expanded) "Collapse log" else "Expand log",
                        tint = ConsoleDim,
                    )
                }
            }
            AnimatedVisibility(expanded) {
                Surface(color = ConsoleHeader, contentColor = ConsoleText) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        itemsIndexed(lines) { _, line ->
                            Text(line, style = MonoStyle, color = colorFor(line))
                        }
                    }
                }
            }
        }
    }
}

private val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)

private fun colorFor(line: String) = when {
    "[BLOCKED]" in line -> ConsoleText
    line.startsWith("ERROR") || line.startsWith("FAILED") || line.startsWith("stderr") -> ConsoleError
    line.startsWith("──") || line.startsWith("$") -> ConsoleRule
    line.startsWith("SKIP") || line.startsWith("  ") -> ConsoleDim
    else -> ConsoleText
}
